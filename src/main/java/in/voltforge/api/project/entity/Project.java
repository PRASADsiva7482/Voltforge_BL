package in.voltforge.api.project.entity;

import in.voltforge.api.common.entity.BaseEntity;
import in.voltforge.api.common.enums.BoardType;
import in.voltforge.api.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "board_type", nullable = false, length = 50)
    @Builder.Default
    private BoardType boardType = BoardType.ARDUINO_UNO;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "canvas_layout", columnDefinition = "json")
    private Map<String, Object> canvasLayout;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "component_config", columnDefinition = "json")
    private Map<String, Object> componentConfig;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "fork_count", nullable = false)
    @Builder.Default
    private Integer forkCount = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "forked_from")
    private Project forkedFrom;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @Column(name = "tags", length = 500)
    private String tags;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CodeFile> codeFiles = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProjectShare> shares = new ArrayList<>();

    public void addCodeFile(CodeFile codeFile) {
        codeFiles.add(codeFile);
        codeFile.setProject(this);
    }

    public void removeCodeFile(CodeFile codeFile) {
        codeFiles.remove(codeFile);
        codeFile.setProject(null);
    }
}
