package com.cooked.backend.repository;

import com.cooked.backend.dto.request.*;
import com.cooked.backend.dto.response.*;
import com.cooked.backend.entity.*;
import com.cooked.backend.exception.BadRequestException;
import com.cooked.backend.service.impl.IngredientCatalogServiceImpl;
import com.cooked.backend.service.impl.UnmatchedIngredientServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Ingredient Visual Library end to end on H2: starter pack, guards, queue, publish. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class IngredientCatalogFlowTest {

    @Autowired private TestEntityManager em;
    @Autowired private IngredientVisualRepository visuals;
    @Autowired private IngredientCatalogReleaseRepository releases;
    @Autowired private UnmatchedIngredientRepository unmatched;
    @Autowired private UnmatchedIngredientUserRepository unmatchedUsers;
    @Autowired private RecipeIngredientRepository recipeIngredients;
    @Autowired private UserRepository users;
    @Autowired private IngredientRepository ingredients;
    @Autowired private PlatformTransactionManager tx;

    private final ObjectMapper json = new ObjectMapper();
    private IngredientCatalogServiceImpl catalog;
    private UnmatchedIngredientServiceImpl queue;

    @BeforeEach
    void setUp() {
        catalog = new IngredientCatalogServiceImpl(visuals, releases, unmatched, users, ingredients, tx, json);
        queue = new UnmatchedIngredientServiceImpl(unmatched, unmatchedUsers, visuals, recipeIngredients, catalog, tx);
        em.persist(User.builder().email("cheikh@test.com").firstname("Cheikh").lastname("Gueye").password("x")
                .role(Role.ADMIN).status(Status.ACTIVE).build());
    }

    private IngredientLibraryResponse library(IngredientVisualFilter f) {
        em.flush();
        em.clear();
        return catalog.library(f, null, 0, 12);
    }

    private static IngredientVisualFilter all() {
        return new IngredientVisualFilter(null, null, null, null, null, null);
    }

    @Test
    void starterPackLibraryAndFilters() {
        CatalogSeedResponse seeded = catalog.installStarterPack("cheikh@test.com");
        assertEquals(12, seeded.created());
        assertEquals(12, catalog.installStarterPack("cheikh@test.com").skipped());

        IngredientLibraryResponse page = library(all());
        assertEquals(12, page.total());
        assertEquals(12, page.kpis().complete());
        assertEquals(12L, page.tabCounts().get("ALL"));
        assertEquals(0, page.release().latestVersion());
        assertTrue(page.categories().contains("Alliums"));

        // search reaches aliases, case/accent/plural-insensitive
        assertEquals("scallion", library(new IngredientVisualFilter("Green Onions", null, null, null, null, null)).items().get(0).canonicalId());
        assertEquals("netetou", library(new IngredientVisualFilter("netetou", null, null, null, null, null)).items().get(0).canonicalId());
        assertEquals(2, library(new IngredientVisualFilter(null, null, null, null, null, IngredientDelivery.CDN)).total());
        assertEquals(2, library(new IngredientVisualFilter(null, null, null, null, "spiceSprinkle", null)).total());
        assertFalse(catalog.meta().reusable().isEmpty());
    }

    @Test
    void duplicateGuardAndAliasConflicts() {
        catalog.installStarterPack("cheikh@test.com");
        em.flush();

        IngredientNameCheckResponse check = catalog.checkName("Spring onions", null);
        assertFalse(check.available());
        assertEquals("scallion", check.owner().canonicalId());
        assertTrue(catalog.checkName("Fufu", null).available());

        BadRequestException dup = assertThrows(BadRequestException.class, () -> catalog.create(new IngredientVisualCreateRequest(
                "Spring onion", "spring_onion", "Alliums", null, null, List.of(), null, null, null, null, null, null), "cheikh@test.com"));
        assertTrue(dup.getMessage().contains("already resolves to Scallion"));

        IngredientVisualResponse chives = catalog.create(new IngredientVisualCreateRequest("Chives", "chives", "Fresh Herbs", "Core",
                "herbSway", List.of("chive", "ciboulette"), null, "scallion", "#3F8F3A", null, null, null), "cheikh@test.com");
        assertEquals(IngredientVisualStatus.NEEDS_REVIEW, chives.status());
        assertEquals(List.of("ciboulette"), chives.aliases()); // "chive" is the name itself
        assertTrue(chives.svg().contains("#3F8F3A"));
        assertEquals("Cheikh G.", chives.updatedBy());
        em.flush();

        IngredientVisualResponse scallion = catalog.get(visuals.findByCanonicalId("scallion").orElseThrow().getId());
        BadRequestException taken = assertThrows(BadRequestException.class, () -> catalog.update(scallion.id(),
                new IngredientVisualUpdateRequest(null, null, null, null, List.of("scallions", "Ciboulette"), null, null, null, null, null),
                "cheikh@test.com"));
        assertTrue(taken.getMessage().contains("already belongs to Chives"));

        IngredientVisualResponse edited = catalog.update(scallion.id(), new IngredientVisualUpdateRequest(null, null, null, "",
                List.of("scallions", "green onion", "salad onion"), null, IngredientDelivery.CDN, null, null, null), "cheikh@test.com");
        em.flush();
        assertEquals(IngredientVisualStatus.MISSING_ANIMATION, edited.status());
        assertEquals(List.of("green onion", "salad onion"), edited.aliases());
        assertTrue(catalog.checkName("spring onion", null).available()); // alias removed

        IngredientVisualResponse disabled = catalog.update(chives.id(), new IngredientVisualUpdateRequest(null, null, null, null, null,
                null, null, null, null, false), "cheikh@test.com");
        assertEquals(IngredientVisualStatus.DISABLED, disabled.status());
        assertThrows(BadRequestException.class, () -> catalog.update(visuals.findByCanonicalId("ingredient_generic").orElseThrow().getId(),
                new IngredientVisualUpdateRequest(null, null, null, null, null, null, null, null, null, false), "cheikh@test.com"));
        assertThrows(BadRequestException.class, () -> catalog.replaceAsset(chives.id(), "<svg viewBox=\"0 0 64 64\"><script/></svg>", "cheikh@test.com"));
    }

    @Test
    void unmatchedQueueSuggestsAndResolves() {
        catalog.installStarterPack("cheikh@test.com");
        em.flush();

        assertEquals(2, queue.record(List.of("Tomatoes", "Scalion", "fufu", "Fufu ", "42"), IngredientNameSource.IMPORT, "ana@test.com"));
        queue.record(List.of("fufu"), IngredientNameSource.SCAN, "bob@test.com");
        queue.record(List.of("fufu"), IngredientNameSource.SCAN, "bob@test.com");
        em.flush();

        UnmatchedQueueResponse open = queue.queue(new UnmatchedIngredientFilter(null, null, UnmatchedStatus.OPEN, 30), null, 0, 25);
        assertEquals(2, open.total());
        UnmatchedIngredientResponse fufu = open.items().get(0);
        assertEquals("fufu", fufu.key());
        assertEquals(3, fufu.seen());
        assertEquals(2, fufu.users());
        assertEquals(IngredientNameSource.SCAN, fufu.source());
        UnmatchedIngredientResponse typo = open.items().get(1);
        assertEquals("scallion", typo.suggestion().canonicalId());
        assertEquals(1, queue.queue(new UnmatchedIngredientFilter(null, IngredientNameSource.SCAN, UnmatchedStatus.OPEN, null), null, 0, 25).total());

        assertEquals(UnmatchedStatus.RESOLVED, queue.addAsAlias(typo.id(), typo.suggestion().id(), "cheikh@test.com").status());
        em.flush();
        assertFalse(catalog.checkName("scalion", null).available());

        IngredientVisualResponse created = catalog.create(new IngredientVisualCreateRequest("Fufu", "fufu", "African pantry", "West African",
                "dairyWobble", List.of("foufou"), null, null, null, null, null, fufu.id()), "cheikh@test.com");
        em.flush();
        assertEquals(IngredientVisualStatus.MISSING_ASSET, created.status());
        assertEquals(UnmatchedStatus.RESOLVED, unmatched.findById(fufu.id()).orElseThrow().getStatus());
        assertEquals(0, queue.queue(new UnmatchedIngredientFilter(null, null, UnmatchedStatus.OPEN, null), null, 0, 25).total());
        assertEquals(0, queue.record(List.of("foufou"), IngredientNameSource.GROCERY, "ana@test.com"));
    }

    @Test
    void backfillQueuesRecipeNamesTheCatalogDoesNotKnow() {
        catalog.installStarterPack("cheikh@test.com");
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe mafe = em.persist(Recipe.builder().name("Mafe").user(ana).build());
        Recipe thieb = em.persist(Recipe.builder().name("Thieb").user(ana).build());
        Ingredient tomato = em.persist(Ingredient.builder().name("Tomatoes").build());
        Ingredient peanut = em.persist(Ingredient.builder().name("Peanut butter").build());
        em.persist(RecipeIngredient.builder().recipe(mafe).ingredient(tomato).quantity("2").build());
        em.persist(RecipeIngredient.builder().recipe(mafe).ingredient(peanut).quantity("1 cup").build());
        em.persist(RecipeIngredient.builder().recipe(thieb).ingredient(peanut).quantity("1").build());
        em.flush();

        CatalogSeedResponse r = queue.backfillFromRecipes(100);
        assertEquals(1, r.created());
        assertEquals(1, r.skipped());
        assertEquals(2, unmatched.findByNameKey("peanut_butter").orElseThrow().getImportCount());
        assertEquals(0, queue.backfillFromRecipes(100).created());
    }

    @Test
    void publishShipsEnabledArtAndBumpsTheVersion() throws Exception {
        assertThrows(BadRequestException.class, () -> catalog.publish(null, "cheikh@test.com"));
        catalog.installStarterPack("cheikh@test.com");
        em.flush();

        IngredientReleaseResponse v1 = catalog.publish("Starter pack", "cheikh@test.com");
        assertEquals(1, v1.version());
        assertEquals(12, v1.ingredientCount());
        JsonNode manifest = json.readTree(catalog.publishedManifest().orElseThrow());
        assertEquals(1, manifest.get("version").asInt());
        assertEquals("ingredient_generic", manifest.get("fallback").asText());
        assertEquals(12, manifest.get("ingredients").size());
        assertEquals(2, json.readTree(catalog.draftManifest()).get("version").asInt());
        em.flush();

        IngredientLibraryResponse page = library(all());
        assertEquals(1, page.release().latestVersion());
        assertEquals(2, page.release().nextVersion());
        assertEquals(2, catalog.publish(null, "cheikh@test.com").version());
        assertEquals(2, catalog.releases().size());
    }

    @Test
    void importAddsEveryUnknownDatabaseIngredientOnce() {
        catalog.installStarterPack("cheikh@test.com");
        User ana = em.persist(User.builder().email("ana@test.com").password("x").role(Role.CLIENT).status(Status.ACTIVE).build());
        Recipe mafe = em.persist(Recipe.builder().name("Mafe").user(ana).build());
        Ingredient peanut = em.persist(Ingredient.builder().name("peanut butter").build());
        em.persist(Ingredient.builder().name("Peanut Butters").build());
        em.persist(Ingredient.builder().name("Tomatoes").build());
        em.persist(Ingredient.builder().name("2 onions").build());
        em.persist(Ingredient.builder().name("Chicken stock").build());
        em.persist(RecipeIngredient.builder().recipe(mafe).ingredient(peanut).quantity("1 cup").build());
        queue.record(List.of("Chicken stocks"), IngredientNameSource.IMPORT, "ana@test.com");
        em.flush();

        ImportProgressResponse first = catalog.importDatabaseIngredients("cheikh@test.com", 1);
        assertEquals(1, first.created());
        assertEquals(1, first.remaining());
        ImportProgressResponse r = catalog.importDatabaseIngredients("cheikh@test.com", 500);
        assertEquals(1, r.created());
        assertEquals(0, r.remaining());
        assertEquals(4, r.skipped());
        IngredientVisual pb = visuals.findByCanonicalId("peanut_butter").orElseThrow();
        assertEquals("Peanut butter", pb.getName());
        assertEquals(IngredientVisualStatus.MISSING_ASSET, pb.getStatus());
        assertEquals("Nuts & Seeds", pb.getCategory());
        assertEquals("Soups & Stocks", visuals.findByCanonicalId("chicken_stock").orElseThrow().getCategory());
        assertEquals(UnmatchedStatus.RESOLVED, unmatched.findByNameKey("chicken_stock").orElseThrow().getStatus());
        assertEquals(0, catalog.importDatabaseIngredients("cheikh@test.com", 500).created());
    }
}
