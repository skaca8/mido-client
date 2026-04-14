package io.github.hyunjun.mido.integration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    private Integer id;
    private String title;
    private String body;
    private Integer userId;
}
