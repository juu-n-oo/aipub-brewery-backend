package io.ten1010.dockerizerbackend.aipub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SelfSubjectReviewResponse {

    @JsonProperty("isAuthenticated")
    private boolean isAuthenticated;

    private String username;
    private String userId;
    private List<String> roles;

}
