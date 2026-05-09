-- =============================================================================
-- VoltForge — V2 Seed Component Library
-- =============================================================================

-- ── Arduino Boards ──────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'Arduino Uno R3', 'BOARD', 'ARDUINO_UNO', 'The classic Arduino Uno R3 microcontroller board based on ATmega328P.',
 '{"voltage": 5, "clockSpeed": "16MHz", "flash": "32KB", "sram": "2KB", "eeprom": "1KB"}',
 '{"digital": [0,1,2,3,4,5,6,7,8,9,10,11,12,13], "analog": ["A0","A1","A2","A3","A4","A5"], "pwm": [3,5,6,9,10,11], "power": ["5V","3.3V","GND","VIN"]}',
 false, 1),

(UUID(), 'Arduino Mega 2560', 'BOARD', 'ARDUINO_MEGA', 'Arduino Mega 2560 with 54 digital I/O pins based on ATmega2560.',
 '{"voltage": 5, "clockSpeed": "16MHz", "flash": "256KB", "sram": "8KB", "eeprom": "4KB"}',
 '{"digital": [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,41,42,43,44,45,46,47,48,49,50,51,52,53], "analog": ["A0","A1","A2","A3","A4","A5","A6","A7","A8","A9","A10","A11","A12","A13","A14","A15"], "pwm": [2,3,4,5,6,7,8,9,10,11,12,13], "power": ["5V","3.3V","GND","VIN"]}',
 false, 2),

(UUID(), 'Arduino Nano', 'BOARD', 'ARDUINO_NANO', 'Compact Arduino Nano board based on ATmega328P.',
 '{"voltage": 5, "clockSpeed": "16MHz", "flash": "32KB", "sram": "2KB", "eeprom": "1KB"}',
 '{"digital": [0,1,2,3,4,5,6,7,8,9,10,11,12,13], "analog": ["A0","A1","A2","A3","A4","A5","A6","A7"], "pwm": [3,5,6,9,10,11], "power": ["5V","3.3V","GND","VIN"]}',
 false, 3);

-- ── ESP32 Boards ────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'ESP32 DevKit V1', 'BOARD', 'ESP32', 'ESP32 development board with WiFi and Bluetooth.',
 '{"voltage": 3.3, "clockSpeed": "240MHz", "flash": "4MB", "sram": "520KB", "wifi": true, "bluetooth": true}',
 '{"gpio": [0,2,4,5,12,13,14,15,16,17,18,19,21,22,23,25,26,27,32,33,34,35,36,39], "analog": [32,33,34,35,36,39], "pwm": [0,2,4,5,12,13,14,15,16,17,18,19,21,22,23,25,26,27], "power": ["3.3V","5V","GND","VIN"]}',
 false, 10),

(UUID(), 'ESP32-S3', 'BOARD', 'ESP32_S3', 'ESP32-S3 with enhanced AI capabilities and USB OTG.',
 '{"voltage": 3.3, "clockSpeed": "240MHz", "flash": "8MB", "sram": "512KB", "wifi": true, "bluetooth": true, "usb_otg": true}',
 '{"gpio": [0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21], "analog": [1,2,3,4,5,6,7,8,9,10], "power": ["3.3V","5V","GND"]}',
 true, 11);

-- ── LEDs ────────────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'Red LED', 'LED', 'LED_STANDARD', 'Standard 5mm red LED.',
 '{"color": "#FF0000", "forwardVoltage": 2.0, "maxCurrent": "20mA", "wavelength": "620-625nm"}',
 '{"anode": 1, "cathode": 2}', false, 20),

(UUID(), 'Green LED', 'LED', 'LED_STANDARD', 'Standard 5mm green LED.',
 '{"color": "#00FF00", "forwardVoltage": 2.2, "maxCurrent": "20mA", "wavelength": "520-525nm"}',
 '{"anode": 1, "cathode": 2}', false, 21),

(UUID(), 'Blue LED', 'LED', 'LED_STANDARD', 'Standard 5mm blue LED.',
 '{"color": "#0000FF", "forwardVoltage": 3.2, "maxCurrent": "20mA", "wavelength": "465-470nm"}',
 '{"anode": 1, "cathode": 2}', false, 22),

(UUID(), 'RGB LED (Common Cathode)', 'LED', 'LED_RGB', 'Common cathode RGB LED for multi-color display.',
 '{"type": "common_cathode", "forwardVoltage": {"red": 2.0, "green": 3.2, "blue": 3.2}, "maxCurrent": "20mA"}',
 '{"red": 1, "green": 2, "blue": 3, "cathode": 4}', false, 23),

(UUID(), 'NeoPixel WS2812B', 'LED', 'LED_NEOPIXEL', 'Individually addressable RGB LED strip.',
 '{"voltage": 5, "protocol": "WS2812B", "colors": 16777216}',
 '{"din": 1, "dout": 2, "vcc": 3, "gnd": 4}', false, 24);

-- ── Sensors ─────────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'DHT22 Temperature & Humidity', 'SENSOR', 'SENSOR_DHT22', 'Digital temperature and humidity sensor.',
 '{"tempRange": "-40 to 80°C", "humidityRange": "0-100%", "accuracy": "±0.5°C", "protocol": "OneWire"}',
 '{"vcc": 1, "data": 2, "gnd": 3}', false, 30),

(UUID(), 'HC-SR04 Ultrasonic', 'SENSOR', 'SENSOR_ULTRASONIC', 'Ultrasonic distance sensor with 2cm-400cm range.',
 '{"range": "2-400cm", "accuracy": "3mm", "frequency": "40kHz"}',
 '{"vcc": 1, "trig": 2, "echo": 3, "gnd": 4}', false, 31),

(UUID(), 'PIR Motion Sensor', 'SENSOR', 'SENSOR_PIR', 'Passive infrared motion detection sensor.',
 '{"range": "7m", "angle": "120°", "delay": "0.3-5s"}',
 '{"vcc": 1, "out": 2, "gnd": 3}', false, 32),

(UUID(), 'LDR Light Sensor', 'SENSOR', 'SENSOR_LDR', 'Light dependent resistor for ambient light detection.',
 '{"resistance_dark": "1MΩ", "resistance_light": "10kΩ"}',
 '{"pin1": 1, "pin2": 2}', false, 33),

(UUID(), 'MPU6050 Accelerometer/Gyro', 'SENSOR', 'SENSOR_IMU', '6-axis accelerometer and gyroscope module.',
 '{"protocol": "I2C", "accelRange": "±16g", "gyroRange": "±2000°/s"}',
 '{"vcc": 1, "gnd": 2, "scl": 3, "sda": 4, "int": 5}', true, 34);

-- ── Displays ────────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'LCD 16x2 (I2C)', 'DISPLAY', 'DISPLAY_LCD_I2C', '16x2 character LCD with I2C backpack.',
 '{"rows": 2, "cols": 16, "protocol": "I2C", "backlight": true}',
 '{"vcc": 1, "gnd": 2, "sda": 3, "scl": 4}', false, 40),

(UUID(), 'OLED 128x64 (SSD1306)', 'DISPLAY', 'DISPLAY_OLED', '0.96 inch OLED display with SSD1306 driver.',
 '{"width": 128, "height": 64, "protocol": "I2C", "color": "white"}',
 '{"vcc": 1, "gnd": 2, "scl": 3, "sda": 4}', false, 41),

(UUID(), '7-Segment Display', 'DISPLAY', 'DISPLAY_7SEG', 'Single digit 7-segment LED display.',
 '{"digits": 1, "type": "common_cathode", "color": "red"}',
 '{"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g":7,"dp":8,"common":9}', false, 42);

-- ── Relays ──────────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'Single Channel Relay', 'RELAY', 'RELAY_SINGLE', '5V single channel relay module.',
 '{"voltage": 5, "maxLoad": "250V/10A", "triggerType": "LOW"}',
 '{"vcc": 1, "gnd": 2, "in": 3, "com": 4, "no": 5, "nc": 6}', false, 50),

(UUID(), '4-Channel Relay Module', 'RELAY', 'RELAY_4CH', '5V 4-channel relay module.',
 '{"voltage": 5, "channels": 4, "maxLoad": "250V/10A", "triggerType": "LOW"}',
 '{"vcc": 1, "gnd": 2, "in1": 3, "in2": 4, "in3": 5, "in4": 6}', false, 51);

-- ── Motors ───────────────────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'DC Motor', 'MOTOR', 'MOTOR_DC', 'Standard DC motor for basic rotation projects.',
 '{"voltage": "3-12V", "rpm": 6000, "current": "150mA"}',
 '{"positive": 1, "negative": 2}', false, 60),

(UUID(), 'Servo Motor (SG90)', 'MOTOR', 'MOTOR_SERVO', 'Micro servo motor with 180° rotation.',
 '{"voltage": "4.8-6V", "angle": 180, "torque": "1.8kg/cm", "speed": "0.12s/60°"}',
 '{"vcc": 1, "gnd": 2, "signal": 3}', false, 61),

(UUID(), 'Stepper Motor (28BYJ-48)', 'MOTOR', 'MOTOR_STEPPER', '5V stepper motor with ULN2003 driver.',
 '{"voltage": 5, "steps": 2048, "gearRatio": "1:64"}',
 '{"in1": 1, "in2": 2, "in3": 3, "in4": 4, "vcc": 5, "gnd": 6}', false, 62);

-- ── Passive Components ──────────────────────────────────────────────────────
INSERT INTO components (id, name, category, type, description, default_properties, pin_config, is_premium, sort_order) VALUES
(UUID(), 'Resistor', 'PASSIVE', 'RESISTOR', 'Through-hole resistor with configurable value.',
 '{"resistance": 220, "unit": "Ω", "tolerance": "5%", "power": "0.25W"}',
 '{"pin1": 1, "pin2": 2}', false, 70),

(UUID(), 'Capacitor', 'PASSIVE', 'CAPACITOR', 'Ceramic capacitor with configurable value.',
 '{"capacitance": 100, "unit": "nF", "voltage": "50V"}',
 '{"pin1": 1, "pin2": 2}', false, 71),

(UUID(), 'Potentiometer', 'PASSIVE', 'POTENTIOMETER', 'Variable resistor with adjustable value.',
 '{"resistance": 10000, "unit": "Ω", "taper": "linear"}',
 '{"pin1": 1, "wiper": 2, "pin2": 3}', false, 72),

(UUID(), 'Push Button', 'PASSIVE', 'BUTTON', 'Momentary tactile push button switch.',
 '{"type": "momentary", "bounceTime": "5ms"}',
 '{"pin1": 1, "pin2": 2}', false, 73),

(UUID(), 'Breadboard', 'PASSIVE', 'BREADBOARD', '830-point solderless breadboard.',
 '{"points": 830, "rows": 63, "powerRails": 4}',
 '{}', false, 74),

(UUID(), 'Buzzer', 'PASSIVE', 'BUZZER', 'Piezo buzzer for audio output.',
 '{"voltage": "3-24V", "frequency": "2kHz", "type": "active"}',
 '{"positive": 1, "negative": 2}', false, 75);
