package io.github.hyunjun.mido.integration;

import io.github.hyunjun.mido.api.BaseExternalApi;
import io.github.hyunjun.mido.config.MidoClientFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class JsonPlaceholderService extends BaseExternalApi {

    private final RestClient client;

    public JsonPlaceholderService(MidoClientFactory midoClientFactory) {
        this.client = midoClientFactory.getOrCreateClient("jsonplaceholder");
    }

    @Override
    protected String getChannelName() {
        return "jsonplaceholder";
    }

    public Post getPost(int id) {
        return withDefaultChannelAction("getPost", () ->
            client.get()
                .uri("/posts/{id}", id)
                .retrieve()
                .body(Post.class)
        );
    }

    public Post createPost(Post post) {
        return withDefaultChannelAction("createPost", () ->
            client.post()
                .uri("/posts")
                .body(post)
                .retrieve()
                .body(Post.class)
        );
    }

    public Post updatePost(int id, Post post) {
        return withDefaultChannelAction("updatePost", () ->
            client.put()
                .uri("/posts/{id}", id)
                .body(post)
                .retrieve()
                .body(Post.class)
        );
    }

    public ResponseEntity<Void> deletePost(int id) {
        return withDefaultChannelAction("deletePost", () ->
            client.delete()
                .uri("/posts/{id}", id)
                .retrieve()
                .toBodilessEntity()
        );
    }
}
