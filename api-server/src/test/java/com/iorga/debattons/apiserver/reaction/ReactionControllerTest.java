package com.iorga.debattons.apiserver.reaction;

import com.iorga.debattons.apiserver.ApiServerApplication;
import com.iorga.debattons.apiserver.util.GraphUtils;
import com.iorga.debattons.apiserver.version.VersionService;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class ReactionControllerTest {

  @Autowired
  private TestRestTemplate restTemplate;

  @Autowired
  private VersionService versionService;

  static TinkerGraph graph = TinkerGraph.open();

  @Configuration
  @Import(ApiServerApplication.class)
  static class Config {
    @Bean
    @Primary
    public GraphUtils.GraphManager createGraphManager() {
      return new GraphUtils.GraphManager() {
        @Override
        public Graph open() {
          return graph;
        }

        @Override
        public String vertexIdToString(Object vertexId) {
          return vertexId.toString();
        }

        @Override
        public Object vertexIdFromString(String vertexId) {
          return Long.parseLong(vertexId);
        }
      };
    }
  }

  @Before
  public void setUp() throws Exception {
    // set the GraphManager to be in memory & bootstrap the graph
    graph = TinkerGraph.open();
    versionService.bootstrap();
  }

  @After
  public void tearDown() {
    graph.close();
  }


  @Test
  public void createTest() throws IOException {
    Reaction reaction = newTestReaction();
    Reaction reactionOut = createReaction(reaction);
    assertThat(reactionOut.getTitle()).isEqualTo(reaction.getTitle());
    assertThat(reactionOut.getContent()).isEqualTo(reaction.getContent());
    assertThat(reactionOut.getId()).isNotEmpty();
  }

  private Reaction createReaction(Reaction reaction) {
    return restTemplate.postForObject("/reactions", reaction, Reaction.class);
  }

  private Reaction createReaction(Reaction reaction, String reactingToReactionId) throws UnsupportedEncodingException {
    return restTemplate.postForObject("/reactions?reactToReactionId=" + URLEncoder.encode(reactingToReactionId, "UTF-8"), reaction, Reaction.class);
  }

  private Reaction newTestReaction() {
    return newTestReaction("");
  }

  private Reaction newTestReaction(String label) {
    Reaction reaction = new Reaction();
    reaction.setTitle("Test Title" + label);
    reaction.setContent("Test content lorem ipsum" + label);
    return reaction;
  }

  @Test
  public void createReactingToTest() throws IOException {
    Reaction reaction = newTestReaction();
    Reaction reactionOut = createReaction(reaction);

    Reaction reaction2 = newTestReaction(" 2");
    Reaction reactionOut2 = createReaction(reaction2, reactionOut.getId());

    assertThat(reactionOut2.getTitle()).isEqualTo(reaction2.getTitle());
    assertThat(reactionOut2.getContent()).isEqualTo(reaction2.getContent());
    assertThat(reactionOut2.getId()).isNotEmpty();
  }

  @Test
  public void findRootsTest() {
    Reaction reaction = newTestReaction();
    createReaction(reaction);
    reaction.setTitle("Test title 2");
    createReaction(reaction);
    List<Reaction> reactions = restTemplate.exchange("/reactions/roots", HttpMethod.GET, null, new ParameterizedTypeReference<List<Reaction>>() {
    }).getBody();
    assertThat(reactions).hasSize(2);
    assertThat(reactions.get(0).getTitle()).isEqualTo(reaction.getTitle()); // Latest reaction must be the first one to be listed
  }

  @Test
  public void findByIdTest() {
    Reaction reaction = newTestReaction();
    Reaction createdReaction = createReaction(reaction);
    Reaction foundReaction = restTemplate.getForObject("/reactions/" + createdReaction.getId(), Reaction.class);
    assertThat(foundReaction.getTitle()).isEqualTo(reaction.getTitle());
  }

  @Test
  public void findByIdLoadingReactedToDepthTest() throws UnsupportedEncodingException {
    Reaction reaction = newTestReaction();
    Reaction reactionOut = createReaction(reaction);
    Reaction reaction2 = newTestReaction(" 2");
    Reaction reactionOut2 = createReaction(reaction, reactionOut.getId());
    Reaction reaction3 = newTestReaction(" 3");
    Reaction reactionOut3 = createReaction(reaction, reactionOut.getId());

    Reaction foundReaction = restTemplate.getForObject("/reactions/" + reactionOut.getId() + "?reactedToDepth=1", Reaction.class);
    assertThat(foundReaction.getTitle()).isEqualTo(reaction.getTitle());
    assertThat(foundReaction.getReactedTo()).hasSize(2);
  }
}
