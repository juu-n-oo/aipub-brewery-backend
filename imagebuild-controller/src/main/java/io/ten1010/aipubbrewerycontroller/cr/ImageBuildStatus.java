package io.ten1010.aipubbrewerycontroller.cr;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageBuildStatus {

    private String phase;
    private String startTime;
    private String completionTime;
    private String message;
    private String imageDigest;

}
