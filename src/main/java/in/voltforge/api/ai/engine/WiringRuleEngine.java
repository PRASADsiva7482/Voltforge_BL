package in.voltforge.api.ai.engine;

import in.voltforge.api.ai.dto.AiWireSuggestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * VoltForge Rule-Based Wiring Engine
 *
 * A deterministic, zero-latency wiring AI that uses electronics knowledge
 * to generate correct wire connections between components.
 * No external model (Ollama/GPT) required — pure Java rule engine.
 *
 * Rules are based on standard Arduino/ESP32 wiring conventions:
 * - Power: VCC/5V/3.3V → component power pins
 * - Ground: GND → component ground pins
 * - Signal: MCU digital/analog pins → component signal/data pins
 * - I2C: SDA+SCL between MCU and I2C devices
 * - Motor: MCU → driver → motor pattern
 */
@Slf4j
@Component
public class WiringRuleEngine {

    // ── Color conventions ──
    private static final String COLOR_POWER   = "#ef4444"; // Red
    private static final String COLOR_GROUND  = "#555555"; // Dark gray
    private static final String COLOR_SIGNAL  = "#3b82f6"; // Blue
    private static final String COLOR_DATA    = "#22c55e"; // Green
    private static final String COLOR_PWM     = "#f59e0b"; // Amber
    private static final String COLOR_I2C     = "#a855f7"; // Purple
    private static final String COLOR_SERIAL  = "#06b6d4"; // Cyan

    // ── Component categories ──
    private static final Set<String> MCU_TYPES = Set.of(
            "ARDUINO_UNO", "ARDUINO_MEGA", "ARDUINO_NANO", "ESP32", "ESP32_S3", "ESP8266"
    );
    private static final Set<String> LED_TYPES = Set.of("LED_STANDARD", "LED_RGB", "LED_NEOPIXEL");
    private static final Set<String> SENSOR_TYPES = Set.of(
            "SENSOR_DHT11", "SENSOR_DHT22", "TEMP_SENSOR", "SENSOR_ULTRASONIC", "ULTRASONIC_SENSOR",
            "SENSOR_PIR", "PIR_SENSOR", "SENSOR_LDR", "LDR", "SENSOR_IMU", "SOIL_MOISTURE", "IR_RECEIVER"
    );
    private static final Set<String> DISPLAY_TYPES = Set.of(
            "DISPLAY_LCD_I2C", "LCD_16X2", "DISPLAY_OLED", "OLED_DISPLAY", "DISPLAY_7SEG"
    );
    private static final Set<String> I2C_TYPES = Set.of(
            "DISPLAY_LCD_I2C", "DISPLAY_OLED", "OLED_DISPLAY", "SENSOR_IMU"
    );
    private static final Set<String> MOTOR_TYPES = Set.of(
            "MOTOR_DC", "MOTOR_SERVO", "SERVO_MOTOR", "MOTOR_STEPPER", "STEPPER_MOTOR"
    );
    private static final Set<String> PASSIVE_TYPES = Set.of("RESISTOR", "CAPACITOR", "POTENTIOMETER");
    private static final Set<String> RELAY_TYPES = Set.of("RELAY_SINGLE", "RELAY_SPDT", "RELAY_4CH");

    /**
     * Main entry: given a list of components on the canvas, generate wiring suggestions.
     *
     * @param components List of maps with keys: id, type, name, pins (list of pin maps)
     * @param boardType  The MCU board type (e.g., "ARDUINO_UNO")
     * @return list of wire suggestions
     */
    public List<AiWireSuggestion> generateWiringSuggestions(
            List<Map<String, Object>> components, String boardType) {

        if (components == null || components.size() < 2) {
            return Collections.emptyList();
        }

        List<AiWireSuggestion> suggestions = new ArrayList<>();
        Set<String> usedConnections = new HashSet<>();

        // Find the MCU (main board)
        Map<String, Object> mcu = findMcu(components);
        if (mcu == null) {
            log.warn("No MCU found on canvas, cannot generate wiring");
            return suggestions;
        }

        String mcuId = str(mcu, "id");
        String mcuType = str(mcu, "type");
        List<Map<String, Object>> mcuPins = getPins(mcu);

        // Track which MCU pins are assigned
        Set<String> assignedMcuPins = new HashSet<>();
        int nextDigitalPin = 2; // Start from D2 (D0/D1 reserved for Serial)
        int nextAnalogPin = 0;  // Start from A0

        // Process each non-MCU component
        for (Map<String, Object> comp : components) {
            String compId = str(comp, "id");
            String compType = str(comp, "type");
            if (MCU_TYPES.contains(compType) || compId.equals(mcuId)) continue;

            List<Map<String, Object>> compPins = getPins(comp);

            // ── Power + Ground connections ──
            wirePowerAndGround(suggestions, usedConnections, mcuId, mcuPins, compId, compType, compPins, assignedMcuPins);

            // ── Signal connections by component type ──
            if (LED_TYPES.contains(compType)) {
                wireLed(suggestions, usedConnections, mcuId, mcuPins, compId, compType, compPins, assignedMcuPins, nextDigitalPin);
                nextDigitalPin = advanceDigitalPin(nextDigitalPin, compType.equals("LED_RGB") ? 3 : 1);
            } else if (SENSOR_TYPES.contains(compType)) {
                wireSensor(suggestions, usedConnections, mcuId, mcuType, mcuPins, compId, compType, compPins, assignedMcuPins, nextDigitalPin, nextAnalogPin);
                if (isAnalogSensor(compType)) {
                    nextAnalogPin++;
                } else {
                    nextDigitalPin = advanceDigitalPin(nextDigitalPin, getSignalPinCount(compType));
                }
            } else if (DISPLAY_TYPES.contains(compType)) {
                wireDisplay(suggestions, usedConnections, mcuId, mcuType, mcuPins, compId, compType, compPins, assignedMcuPins, nextDigitalPin);
                if (!I2C_TYPES.contains(compType)) {
                    nextDigitalPin = advanceDigitalPin(nextDigitalPin, 6); // RS, E, D4-D7
                }
            } else if (MOTOR_TYPES.contains(compType)) {
                wireMotor(suggestions, usedConnections, mcuId, mcuPins, compId, compType, compPins, assignedMcuPins, nextDigitalPin);
                nextDigitalPin = advanceDigitalPin(nextDigitalPin, compType.contains("STEPPER") ? 4 : 1);
            } else if (compType.equals("BUZZER")) {
                wireBuzzer(suggestions, usedConnections, mcuId, mcuPins, compId, compPins, assignedMcuPins, nextDigitalPin);
                nextDigitalPin = advanceDigitalPin(nextDigitalPin, 1);
            } else if (compType.equals("BUTTON") || compType.equals("PUSH_BUTTON")) {
                wireButton(suggestions, usedConnections, mcuId, mcuPins, compId, compPins, assignedMcuPins, nextDigitalPin);
                nextDigitalPin = advanceDigitalPin(nextDigitalPin, 1);
            } else if (compType.equals("POTENTIOMETER")) {
                wirePotentiometer(suggestions, usedConnections, mcuId, mcuPins, compId, compPins, assignedMcuPins, nextAnalogPin);
                nextAnalogPin++;
            } else if (RELAY_TYPES.contains(compType)) {
                wireRelay(suggestions, usedConnections, mcuId, mcuPins, compId, compPins, assignedMcuPins, nextDigitalPin);
                nextDigitalPin = advanceDigitalPin(nextDigitalPin, 1);
            }
        }

        log.info("Rule engine generated {} wire suggestions", suggestions.size());
        return suggestions;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  WIRING RULES BY COMPONENT TYPE
    // ══════════════════════════════════════════════════════════════════════

    private void wirePowerAndGround(List<AiWireSuggestion> suggestions, Set<String> used,
                                     String mcuId, List<Map<String, Object>> mcuPins,
                                     String compId, String compType, List<Map<String, Object>> compPins,
                                     Set<String> assigned) {
        // Connect VCC pin
        String vccPin = findPin(compPins, "vcc", "vdd", "v+", "3v3", "vin");
        if (vccPin != null) {
            String mcuPower = findPin(mcuPins, "5v", "3v3");
            if (mcuPower != null) {
                addSuggestion(suggestions, used, mcuId, mcuPower, compId, vccPin,
                        COLOR_POWER, "Power: MCU → " + compType);
            }
        }

        // Connect GND pin
        String gndPin = findPin(compPins, "gnd", "gnd1", "gnd2", "vss", "neg", "com");
        if (gndPin != null) {
            String mcuGnd = findPin(mcuPins, "gnd1", "gnd2", "gnd");
            if (mcuGnd != null) {
                addSuggestion(suggestions, used, mcuId, mcuGnd, compId, gndPin,
                        COLOR_GROUND, "Ground: MCU → " + compType);
            }
        }
    }

    private void wireLed(List<AiWireSuggestion> suggestions, Set<String> used,
                          String mcuId, List<Map<String, Object>> mcuPins,
                          String compId, String compType, List<Map<String, Object>> compPins,
                          Set<String> assigned, int nextPin) {
        if (compType.equals("LED_RGB")) {
            // RGB LED: 3 signal pins
            for (String colorPin : new String[]{"r", "g", "b"}) {
                String pin = findPin(compPins, colorPin);
                if (pin != null) {
                    String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
                    if (mcuPin != null) {
                        addSuggestion(suggestions, used, mcuId, mcuPin, compId, pin,
                                COLOR_PWM, "RGB " + colorPin.toUpperCase() + " → D" + nextPin);
                        assigned.add(mcuPin);
                        nextPin++;
                    }
                }
            }
        } else {
            // Standard LED: anode to digital pin
            String anode = findPin(compPins, "anode", "a", "p1", "+");
            if (anode != null) {
                String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, anode,
                            COLOR_SIGNAL, "LED Signal → D" + nextPin);
                    assigned.add(mcuPin);
                }
            }
        }
    }

    private void wireSensor(List<AiWireSuggestion> suggestions, Set<String> used,
                             String mcuId, String mcuType, List<Map<String, Object>> mcuPins,
                             String compId, String compType, List<Map<String, Object>> compPins,
                             Set<String> assigned, int nextDigital, int nextAnalog) {
        if (isAnalogSensor(compType)) {
            // Analog sensors (LDR, soil moisture) → analog pin
            String sigPin = findPin(compPins, "sig", "out", "data", "p1", "p2");
            if (sigPin != null) {
                String mcuPin = findAnalogPin(mcuPins, nextAnalog, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, sigPin,
                            COLOR_DATA, compType + " → A" + nextAnalog);
                    assigned.add(mcuPin);
                }
            }
        } else if (compType.contains("ULTRASONIC")) {
            // Ultrasonic: TRIG + ECHO
            String trigPin = findPin(compPins, "trig");
            String echoPin = findPin(compPins, "echo");
            if (trigPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextDigital, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, trigPin,
                            COLOR_SIGNAL, "Ultrasonic TRIG → D" + nextDigital);
                    assigned.add(mcuPin);
                }
            }
            if (echoPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextDigital + 1, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, echoPin,
                            COLOR_DATA, "Ultrasonic ECHO → D" + (nextDigital + 1));
                    assigned.add(mcuPin);
                }
            }
        } else if (compType.contains("DHT")) {
            // DHT11/22: single data pin
            String dataPin = findPin(compPins, "data", "dat", "out");
            if (dataPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextDigital, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, dataPin,
                            COLOR_DATA, compType + " DATA → D" + nextDigital);
                    assigned.add(mcuPin);
                }
            }
        } else if (compType.contains("PIR")) {
            String outPin = findPin(compPins, "out", "sig", "data");
            if (outPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextDigital, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, outPin,
                            COLOR_DATA, "PIR OUT → D" + nextDigital);
                    assigned.add(mcuPin);
                }
            }
        } else if (compType.contains("IMU")) {
            // IMU is I2C
            wireI2C(suggestions, used, mcuId, mcuType, mcuPins, compId, compPins, assigned);
        } else {
            // Generic sensor: find signal pin
            String sigPin = findPin(compPins, "out", "sig", "data", "s");
            if (sigPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextDigital, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, sigPin,
                            COLOR_DATA, compType + " → D" + nextDigital);
                    assigned.add(mcuPin);
                }
            }
        }
    }

    private void wireDisplay(List<AiWireSuggestion> suggestions, Set<String> used,
                              String mcuId, String mcuType, List<Map<String, Object>> mcuPins,
                              String compId, String compType, List<Map<String, Object>> compPins,
                              Set<String> assigned, int nextPin) {
        if (I2C_TYPES.contains(compType)) {
            wireI2C(suggestions, used, mcuId, mcuType, mcuPins, compId, compPins, assigned);
        }
        // Non-I2C displays (LCD 16x2 parallel) need RS, E, D4-D7 — complex wiring
    }

    private void wireI2C(List<AiWireSuggestion> suggestions, Set<String> used,
                          String mcuId, String mcuType, List<Map<String, Object>> mcuPins,
                          String compId, List<Map<String, Object>> compPins, Set<String> assigned) {
        String sdaPin = findPin(compPins, "sda", "dat");
        String sclPin = findPin(compPins, "scl", "clk");
        // MCU I2C pins
        String mcuSda = findPin(mcuPins, "a4", "sda");
        String mcuScl = findPin(mcuPins, "a5", "scl");
        // ESP32 uses different pins for I2C
        if (mcuType.contains("ESP")) {
            mcuSda = findPinContaining(mcuPins, "21", "sda");
            mcuScl = findPinContaining(mcuPins, "22", "scl");
        }
        if (sdaPin != null && mcuSda != null) {
            addSuggestion(suggestions, used, mcuId, mcuSda, compId, sdaPin,
                    COLOR_I2C, "I2C SDA");
            assigned.add(mcuSda);
        }
        if (sclPin != null && mcuScl != null) {
            addSuggestion(suggestions, used, mcuId, mcuScl, compId, sclPin,
                    COLOR_I2C, "I2C SCL");
            assigned.add(mcuScl);
        }
    }

    private void wireMotor(List<AiWireSuggestion> suggestions, Set<String> used,
                            String mcuId, List<Map<String, Object>> mcuPins,
                            String compId, String compType, List<Map<String, Object>> compPins,
                            Set<String> assigned, int nextPin) {
        if (compType.contains("SERVO")) {
            String sigPin = findPin(compPins, "sig", "signal", "pwm", "s");
            if (sigPin != null) {
                String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
                if (mcuPin != null) {
                    addSuggestion(suggestions, used, mcuId, mcuPin, compId, sigPin,
                            COLOR_PWM, "Servo Signal → D" + nextPin);
                    assigned.add(mcuPin);
                }
            }
        } else if (compType.contains("STEPPER")) {
            for (String pinName : new String[]{"a1", "a2", "b1", "b2"}) {
                String pin = findPin(compPins, pinName);
                if (pin != null) {
                    String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
                    if (mcuPin != null) {
                        addSuggestion(suggestions, used, mcuId, mcuPin, compId, pin,
                                COLOR_SIGNAL, "Stepper " + pinName.toUpperCase() + " → D" + nextPin);
                        assigned.add(mcuPin);
                        nextPin++;
                    }
                }
            }
        }
    }

    private void wireBuzzer(List<AiWireSuggestion> suggestions, Set<String> used,
                             String mcuId, List<Map<String, Object>> mcuPins,
                             String compId, List<Map<String, Object>> compPins,
                             Set<String> assigned, int nextPin) {
        String posPin = findPin(compPins, "pos", "+", "sig", "s");
        if (posPin != null) {
            String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
            if (mcuPin != null) {
                addSuggestion(suggestions, used, mcuId, mcuPin, compId, posPin,
                        COLOR_SIGNAL, "Buzzer → D" + nextPin);
                assigned.add(mcuPin);
            }
        }
    }

    private void wireButton(List<AiWireSuggestion> suggestions, Set<String> used,
                              String mcuId, List<Map<String, Object>> mcuPins,
                              String compId, List<Map<String, Object>> compPins,
                              Set<String> assigned, int nextPin) {
        // Button: one side to MCU digital pin, other side to GND (INPUT_PULLUP)
        String pin1 = findPin(compPins, "p1a", "p1", "1a", "a");
        if (pin1 != null) {
            String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
            if (mcuPin != null) {
                addSuggestion(suggestions, used, mcuId, mcuPin, compId, pin1,
                        COLOR_SIGNAL, "Button → D" + nextPin + " (INPUT_PULLUP)");
                assigned.add(mcuPin);
            }
        }
        // Other side to GND
        String pin2 = findPin(compPins, "p2a", "p2", "2a", "b");
        if (pin2 != null) {
            String mcuGnd = findPin(mcuPins, "gnd1", "gnd2", "gnd");
            if (mcuGnd != null) {
                addSuggestion(suggestions, used, mcuId, mcuGnd, compId, pin2,
                        COLOR_GROUND, "Button GND");
            }
        }
    }

    private void wirePotentiometer(List<AiWireSuggestion> suggestions, Set<String> used,
                                    String mcuId, List<Map<String, Object>> mcuPins,
                                    String compId, List<Map<String, Object>> compPins,
                                    Set<String> assigned, int nextAnalog) {
        // Wiper to analog pin
        String wiperPin = findPin(compPins, "wiper", "w", "out");
        if (wiperPin != null) {
            String mcuPin = findAnalogPin(mcuPins, nextAnalog, assigned);
            if (mcuPin != null) {
                addSuggestion(suggestions, used, mcuId, mcuPin, compId, wiperPin,
                        COLOR_DATA, "Potentiometer → A" + nextAnalog);
                assigned.add(mcuPin);
            }
        }
        // P1 to 5V, P2 to GND
        String p1 = findPin(compPins, "p1");
        if (p1 != null) {
            String mcuPower = findPin(mcuPins, "5v", "3v3");
            if (mcuPower != null) {
                addSuggestion(suggestions, used, mcuId, mcuPower, compId, p1,
                        COLOR_POWER, "Pot VCC");
            }
        }
        String p2 = findPin(compPins, "p2");
        if (p2 != null) {
            String mcuGnd = findPin(mcuPins, "gnd1", "gnd2");
            if (mcuGnd != null) {
                addSuggestion(suggestions, used, mcuId, mcuGnd, compId, p2,
                        COLOR_GROUND, "Pot GND");
            }
        }
    }

    private void wireRelay(List<AiWireSuggestion> suggestions, Set<String> used,
                            String mcuId, List<Map<String, Object>> mcuPins,
                            String compId, List<Map<String, Object>> compPins,
                            Set<String> assigned, int nextPin) {
        String coilPin = findPin(compPins, "coil1", "in", "sig", "s");
        if (coilPin != null) {
            String mcuPin = findDigitalPin(mcuPins, nextPin, assigned);
            if (mcuPin != null) {
                addSuggestion(suggestions, used, mcuId, mcuPin, compId, coilPin,
                        COLOR_SIGNAL, "Relay IN → D" + nextPin);
                assigned.add(mcuPin);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PIN FINDER HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private Map<String, Object> findMcu(List<Map<String, Object>> components) {
        return components.stream()
                .filter(c -> MCU_TYPES.contains(str(c, "type")))
                .findFirst().orElse(null);
    }

    private String findPin(List<Map<String, Object>> pins, String... names) {
        for (String name : names) {
            for (Map<String, Object> pin : pins) {
                String pinId = str(pin, "id").toLowerCase();
                String pinName = str(pin, "name").toLowerCase();
                if (pinId.equals(name.toLowerCase()) || pinName.equals(name.toLowerCase()) ||
                    pinId.contains(name.toLowerCase()) || pinName.contains(name.toLowerCase())) {
                    return str(pin, "id");
                }
            }
        }
        return null;
    }

    private String findPinContaining(List<Map<String, Object>> pins, String... fragments) {
        for (String frag : fragments) {
            for (Map<String, Object> pin : pins) {
                String pinName = str(pin, "name").toLowerCase();
                if (pinName.contains(frag.toLowerCase())) {
                    return str(pin, "id");
                }
            }
        }
        return null;
    }

    private String findDigitalPin(List<Map<String, Object>> mcuPins, int pinNum, Set<String> assigned) {
        // Try exact match first: d2, d3, etc.
        for (Map<String, Object> pin : mcuPins) {
            String id = str(pin, "id").toLowerCase();
            String name = str(pin, "name").toLowerCase();
            if ((id.equals("d" + pinNum) || name.startsWith("d" + pinNum)) && !assigned.contains(str(pin, "id"))) {
                return str(pin, "id");
            }
        }
        // Fallback: any unassigned digital pin
        for (Map<String, Object> pin : mcuPins) {
            String name = str(pin, "name").toLowerCase();
            if (name.startsWith("d") && !name.contains("sda") && !name.contains("scl") && !assigned.contains(str(pin, "id"))) {
                return str(pin, "id");
            }
        }
        return null;
    }

    private String findAnalogPin(List<Map<String, Object>> mcuPins, int pinNum, Set<String> assigned) {
        for (Map<String, Object> pin : mcuPins) {
            String id = str(pin, "id").toLowerCase();
            if (id.equals("a" + pinNum) && !assigned.contains(str(pin, "id"))) {
                return str(pin, "id");
            }
        }
        return null;
    }

    private boolean isAnalogSensor(String type) {
        return type.contains("LDR") || type.contains("SOIL") || type.contains("POTENTIOMETER");
    }

    private int getSignalPinCount(String type) {
        if (type.contains("ULTRASONIC")) return 2; // TRIG + ECHO
        return 1;
    }

    private int advanceDigitalPin(int current, int count) {
        return current + count;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  UTILITY
    // ══════════════════════════════════════════════════════════════════════

    private void addSuggestion(List<AiWireSuggestion> suggestions, Set<String> used,
                                String fromId, String fromPin, String toId, String toPin,
                                String color, String description) {
        String key = fromId + ":" + fromPin + "->" + toId + ":" + toPin;
        String reverseKey = toId + ":" + toPin + "->" + fromId + ":" + fromPin;
        if (used.contains(key) || used.contains(reverseKey)) return;
        used.add(key);
        suggestions.add(AiWireSuggestion.builder()
                .fromComponentId(fromId).fromPin(fromPin)
                .toComponentId(toId).toPin(toPin)
                .color(color).description(description)
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getPins(Map<String, Object> component) {
        Object pins = component.get("pins");
        if (pins instanceof List) return (List<Map<String, Object>>) pins;
        return Collections.emptyList();
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
