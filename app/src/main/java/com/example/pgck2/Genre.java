package com.example.pgck2;

import java.util.UUID;

/**
 * Model class đại diện cho Thể Loại Phim (Genre).
 */
public class Genre {
    private String id;   // Mã định danh duy nhất của thể loại
    private String name; // Tên thể loại (vd: Hành Động, Kinh Dị...)

    /**
     * Constructor rỗng bắt buộc cho Firebase Firestore.
     */
    public Genre() {
    }

    public Genre(String name) {
        this(UUID.randomUUID().toString(), name);
    }

    public Genre(String id, String name) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.name = name != null ? name : "";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
