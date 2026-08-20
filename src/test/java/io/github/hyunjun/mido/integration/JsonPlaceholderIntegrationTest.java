package io.github.hyunjun.mido.integration;

import io.github.hyunjun.mido.config.MidoClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * jsonplaceholder.typicode.com을 대상으로 하는 실제 HTTP 통합 테스트
 * Spring AutoConfiguration이 올바르게 동작하는지 검증
 */
@SpringBootTest(classes = {
        MidoClientAutoConfiguration.class,
        JsonPlaceholderService.class
})
class JsonPlaceholderIntegrationTest {

    @Autowired
    private JsonPlaceholderService jsonPlaceholderService;

    @Test
    void shouldGetPost() {
        // When
        Post post = jsonPlaceholderService.getPost(1);

        // Then
        assertThat(post).isNotNull();
        assertThat(post.getId()).isEqualTo(1);
        assertThat(post.getTitle()).isNotBlank();
        assertThat(post.getBody()).isNotBlank();
        assertThat(post.getUserId()).isNotNull();
    }

    @Test
    void shouldCreatePost() {
        // Given
        Post newPost = Post.builder()
                .title("안녕 세상아")
                .body("테스트 데이터입니다.")
                .userId(1)
                .build();

        // When
        Post created = jsonPlaceholderService.createPost(newPost);

        // Then
        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getTitle()).isEqualTo("안녕 세상아");
        assertThat(created.getBody()).isEqualTo("테스트 데이터입니다.");
        assertThat(created.getUserId()).isEqualTo(1);
    }

    @Test
    void shouldUpdatePost() {
        // Given
        Post updatedPost = Post.builder()
                .id(1)
                .title("수정된 제목")
                .body("내용도 수정되었습니다.")
                .userId(1)
                .build();

        // When
        Post result = jsonPlaceholderService.updatePost(1, updatedPost);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1);
        assertThat(result.getTitle()).isEqualTo("수정된 제목");
        assertThat(result.getBody()).isEqualTo("내용도 수정되었습니다.");
    }

    @Test
    void shouldDeletePost() {
        // When
        ResponseEntity<Void> response = jsonPlaceholderService.deletePost(1);

        // Then
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
