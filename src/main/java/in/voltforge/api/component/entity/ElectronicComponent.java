package in.voltforge.api.component.entity;

import in.voltforge.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

@Entity
@Table(name = "components")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ElectronicComponent extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "default_properties", columnDefinition = "json")
    private Map<String, Object> defaultProperties;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pin_config", columnDefinition = "json")
    private Map<String, Object> pinConfig;

    @Column(name = "icon_url", length = 500)
    private String iconUrl;

    @Column(name = "svg_data", columnDefinition = "TEXT")
    private String svgData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "electrical_specs", columnDefinition = "json")
    private Map<String, Object> electricalSpecs;

    @Column(name = "is_premium", nullable = false)
    @Builder.Default
    private Boolean isPremium = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;
}
