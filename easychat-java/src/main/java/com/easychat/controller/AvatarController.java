package com.easychat.controller;

import com.easychat.entity.config.Appconfig;
import com.easychat.entity.constants.Constants;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.io.File;

@RestController
@RequestMapping("/file")
public class AvatarController {

    @Resource
    private Appconfig appconfig;

    @GetMapping("/avatar/{id}")
    public ResponseEntity<FileSystemResource> avatar(@PathVariable String id) {
        if (id == null || !id.matches("[UG][A-Za-z0-9_-]{1,40}")) {
            return ResponseEntity.notFound().build();
        }
        File file = new File(appconfig.getProjectFolder() + Constants.FILE_PATH
                + Constants.AVATOR_FILE_PATH + id + Constants.IMAGE_SUFFER);
        if (!file.isFile() || !file.canRead()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noCache())
                .lastModified(file.lastModified())
                .body(new FileSystemResource(file));
    }
}
