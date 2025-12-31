package app.domain.testcases;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TestCase {

    private String id;

    // meta
    private String createdAt;
    private String savedAt;

    private String code;
    private String number;

    private String title;
    private String description;

    private List<String> labels;
    private List<String> tags;

    private List<String> steps;

    public TestCase() {
        this.labels = new ArrayList<>();
        this.tags = new ArrayList<>();
        this.steps = new ArrayList<>();
        this.createdAt = "";
        this.savedAt = "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt == null ? "" : createdAt; }

    public String getSavedAt() { return savedAt; }
    public void setSavedAt(String savedAt) { this.savedAt = savedAt == null ? "" : savedAt; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getLabels() { return labels; }
    public void setLabels(List<String> labels) { this.labels = (labels != null) ? labels : new ArrayList<>(); }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = (tags != null) ? tags : new ArrayList<>(); }

    public List<String> getSteps() { return steps; }
    public void setSteps(List<String> steps) { this.steps = (steps != null) ? steps : new ArrayList<>(); }

    public String labelsText() {
        if (labels == null || labels.isEmpty()) return "";
        return String.join(", ", labels);
    }

    public String tagsText() {
        if (tags == null || tags.isEmpty()) return "";
        return String.join(", ", tags);
    }

    public static List<String> parseCsv(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;

        String[] parts = s.split(",");
        for (String p : parts) {
            String t = (p == null) ? "" : p.trim();
            if (t.isEmpty()) continue;
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    public boolean hasAnyLabel(List<String> selected) {
        if (selected == null || selected.isEmpty()) return true;
        if (labels == null || labels.isEmpty()) return false;

        for (String s : selected) {
            if (s == null) continue;
            if (labels.contains(s)) return true;
        }
        return false;
    }

    public boolean hasAnyTag(List<String> selected) {
        if (selected == null || selected.isEmpty()) return true;
        if (tags == null || tags.isEmpty()) return false;

        for (String s : selected) {
            if (s == null) continue;
            if (tags.contains(s)) return true;
        }
        return false;
    }

    public boolean matchesText(String q) {
        if (q == null || q.isBlank()) return true;

        String qq = q.trim().toLowerCase();

        String t = (title == null) ? "" : title.toLowerCase();
        String d = (description == null) ? "" : description.toLowerCase();

        String c = (code == null) ? "" : code.toLowerCase();
        String n = (number == null) ? "" : number.toLowerCase();

        if (t.contains(qq)) return true;
        if (d.contains(qq)) return true;
        if (c.contains(qq)) return true;
        if (n.contains(qq)) return true;

        if (labels != null) {
            for (String s : labels) {
                if (s != null && s.toLowerCase().contains(qq)) return true;
            }
        }

        if (tags != null) {
            for (String s : tags) {
                if (s != null && s.toLowerCase().contains(qq)) return true;
            }
        }

        return false;
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TestCase that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
