package com.cooked.backend.service.impl;

import com.cooked.backend.entity.CategoryType;
import com.cooked.backend.entity.RecipeCategory;
import com.cooked.backend.repository.RecipeCategoryRepository;
import com.cooked.backend.service.TaxonomyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaxonomyServiceImpl implements TaxonomyService {

    private final RecipeCategoryRepository recipeCategoryRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final Map<String, String> CATEGORY_IMAGES = Map.ofEntries(
        Map.entry("High Protein Picks", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Easy Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825799/ai-recipe-app/taxonomy/t8smxtmx8fo5dgu6nq09.png"),
        Map.entry("30-Minute Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825804/ai-recipe-app/taxonomy/nllnev1tt8sbyhzblxnt.png"),
        Map.entry("Healthy Breakfasts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825810/ai-recipe-app/taxonomy/chip5aztermtfu1e1rn7.jpg"),
        Map.entry("Plant-Based Essentials", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825816/ai-recipe-app/taxonomy/uyjylmhr9lv0m6kznfnv.png"),
        Map.entry("Low-Carb Meals", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825822/ai-recipe-app/taxonomy/rcnlugjoz4wgha3wbip1.png"),
        Map.entry("Bowls & Salads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825828/ai-recipe-app/taxonomy/pfftssqpcneggog8skvr.jpg"),
        Map.entry("Desserts", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825832/ai-recipe-app/taxonomy/jsf51txzntu3ouv8lecr.jpg"),
        Map.entry("Pasta & Noodles", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825833/ai-recipe-app/taxonomy/zl9pii59boskpsazbsf9.jpg"),
        Map.entry("Pizza & Flatbreads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825835/ai-recipe-app/taxonomy/d9g359mpsmvqvdkbah7u.jpg"),
        Map.entry("Curries & Stews", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825839/ai-recipe-app/taxonomy/zkaveyb5ror0ogsxa2a3.jpg"),
        Map.entry("Handheld / Street Food", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825849/ai-recipe-app/taxonomy/dbnakseazqptrnppmxh4.jpg"),
        Map.entry("Soups", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825857/ai-recipe-app/taxonomy/lxx4ebaywhubtnt56s7f.jpg"),
        Map.entry("Rice & Grain", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825868/ai-recipe-app/taxonomy/cnbaetrmnteyizx4wt9g.jpg"),
        Map.entry("Stir Fry", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825877/ai-recipe-app/taxonomy/uxszdotoyanj9raincra.jpg"),
        Map.entry("Dinner", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778830952/ai-recipe-app/taxonomy/ixxrzfdsu511skt49mfi.jpg"),
        Map.entry("Appetizer", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Bowls/Salads", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Curries/Stews", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png"),
        Map.entry("Sides/Snacks", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825794/ai-recipe-app/taxonomy/gjndqlconjidynpzgljm.png")
    );

    private static final Map<String, String> CUISINE_IMAGES = Map.ofEntries(
        Map.entry("American", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825964/ai-recipe-app/taxonomy/edm1eoulgyyj6ajo9sfx.jpg"),
        Map.entry("Senegalese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825968/ai-recipe-app/taxonomy/qtghqgyehufyrqvohxcy.jpg"),
        Map.entry("Vietnamese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825974/ai-recipe-app/taxonomy/ea3iepmpd92dykzekamg.jpg"),
        Map.entry("Moroccan", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825987/ai-recipe-app/taxonomy/cfn9mjfjtxbz6c0l4vsi.jpg"),
        Map.entry("Asian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825996/ai-recipe-app/taxonomy/fsfudpdnqvfqyqtwpwhj.jpg"),
        Map.entry("Brazilian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826007/ai-recipe-app/taxonomy/rfdrinxhadmq6lsaj6uw.jpg"),
        Map.entry("British", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778826016/ai-recipe-app/taxonomy/mr6jkzdtemoulb4p436p.jpg"),
        Map.entry("Malaysian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1778825887/ai-recipe-app/taxonomy/kpf7vnohqamnafqlj9xn.png"),
        Map.entry("Chinese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689910/ai-recipe-app/taxonomy/mobile/slqx0yh4veydvqy2xfdo.png"),
        Map.entry("Mediterranean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689911/ai-recipe-app/taxonomy/mobile/g0llkzmyug42ujox3wly.png"),
        Map.entry("Middle Eastern", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689911/ai-recipe-app/taxonomy/mobile/e9pbufcbgpa9b5rz5ii4.png"),
        Map.entry("Caribbean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689912/ai-recipe-app/taxonomy/mobile/amguuks3jhh1g7mu99gh.png"),
        Map.entry("Korean", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689913/ai-recipe-app/taxonomy/mobile/fcsn6ayfvmhbngfyf6uo.png"),
        Map.entry("Indian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689920/ai-recipe-app/taxonomy/mobile/fb2ii6q2vyaaifws5q7l.png"),
        Map.entry("Japanese", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689920/ai-recipe-app/taxonomy/mobile/ebblzrqj1jhztrgkaw8w.png"),
        Map.entry("West African", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689921/ai-recipe-app/taxonomy/mobile/gbhcf9wrmoliutmbn9o8.png"),
        Map.entry("Italian", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689914/ai-recipe-app/taxonomy/mobile/osb1m8xkkfa34bqr9iom.png"),
        Map.entry("Greek", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689915/ai-recipe-app/taxonomy/mobile/a7z6ffnulh9j6oprzvbf.png"),
        Map.entry("East African", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779696992/ai-recipe-app/taxonomy/mobile/qwk3fstsiocuj0fcxoq6.png"),
        Map.entry("International", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689915/ai-recipe-app/taxonomy/mobile/y9smbi1wnkrizx8t1bzg.png"),
        Map.entry("Others", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779696993/ai-recipe-app/taxonomy/mobile/z7s5n9ejagrm8a0qqtrb.png"),
        Map.entry("Mexican", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689916/ai-recipe-app/taxonomy/mobile/ougjnxvjdm7zzk7jzgxo.png"),
        Map.entry("Thai", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689917/ai-recipe-app/taxonomy/mobile/obvmdxetm6t4nuvtbhti.png"),
        Map.entry("Spanish", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689918/ai-recipe-app/taxonomy/mobile/mne0rmq7cdw8dwjvs49l.png"),
        Map.entry("French", "https://res.cloudinary.com/davj7mdjj/image/upload/v1779689919/ai-recipe-app/taxonomy/mobile/ltdjrtqlre1kx5jikauf.png")
    );

    @Override
    @Transactional
    public RecipeCategory getOrCreateCategory(String name, CategoryType type) {
        if (name == null || name.isBlank()) return null;
        
        String normalizedName = name.trim();
        if (type == CategoryType.CATEGORY) {
            if (normalizedName.equalsIgnoreCase("Breakfast")) normalizedName = "Healthy Breakfasts";
            if (normalizedName.equalsIgnoreCase("Handheld Street Food")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Rice & Grain Dishes")) normalizedName = "Rice & Grain";
            if (normalizedName.equalsIgnoreCase("Stir Fry Sauté Wok")) normalizedName = "Stir Fry";
            if (normalizedName.equalsIgnoreCase("High Protein")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Protein Plates")) normalizedName = "High Protein Picks";
            if (normalizedName.equalsIgnoreCase("Sides & Snacks")) normalizedName = "Handheld / Street Food";
            if (normalizedName.equalsIgnoreCase("Lunch")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Quick Lunches")) normalizedName = "30-Minute Meals";
            if (normalizedName.equalsIgnoreCase("Vegan Essentials")) normalizedName = "Plant-Based Essentials";
            if (normalizedName.equalsIgnoreCase("Comfort Food")) normalizedName = "Pizza & Flatbreads";
            if (normalizedName.equalsIgnoreCase("Meal Prep Favorites")) normalizedName = "Healthy Breakfasts";
        } else if (type == CategoryType.CUISINE) {
            if (normalizedName.equalsIgnoreCase("Italy")) normalizedName = "Italian";
            if (normalizedName.equalsIgnoreCase("Mexico")) normalizedName = "Mexican";
            if (normalizedName.equalsIgnoreCase("Japan")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("France")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("Thailand")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("China")) normalizedName = "Chinese";
            if (normalizedName.equalsIgnoreCase("India")) normalizedName = "Indian";
            if (normalizedName.equalsIgnoreCase("South Korea")) normalizedName = "Korean";
            if (normalizedName.equalsIgnoreCase("Middle East")) normalizedName = "Middle Eastern";
            if (normalizedName.equalsIgnoreCase("West Africa")) normalizedName = "West African";
            if (normalizedName.equalsIgnoreCase("North American")) normalizedName = "American";
            if (normalizedName.equalsIgnoreCase("Japan")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Japan Fusion")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Thai/Chinese")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("South East Asian")) normalizedName = "Thai";
            if (normalizedName.equalsIgnoreCase("Taiwanese")) normalizedName = "Chinese";
            if (normalizedName.equalsIgnoreCase("Argentine")) normalizedName = "Brazilian";
            if (normalizedName.equalsIgnoreCase("Belgian")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("European")) normalizedName = "French";
            if (normalizedName.equalsIgnoreCase("Hawaiian")) normalizedName = "American";
            if (normalizedName.equalsIgnoreCase("Greece")) normalizedName = "Greek";
            if (normalizedName.equalsIgnoreCase("Spain")) normalizedName = "Spanish";
            if (normalizedName.equalsIgnoreCase("Brazil")) normalizedName = "Brazilian";
            if (normalizedName.equalsIgnoreCase("Turkey")) normalizedName = "Turkish";
            if (normalizedName.equalsIgnoreCase("Lebanon")) normalizedName = "Lebanese";
            if (normalizedName.equalsIgnoreCase("Vietnam")) normalizedName = "Vietnamese";
            if (normalizedName.equalsIgnoreCase("Morocco")) normalizedName = "Moroccan";
            if (normalizedName.equalsIgnoreCase("Japanese Fusion")) normalizedName = "Japanese";
            if (normalizedName.equalsIgnoreCase("Wast African")) normalizedName = "West African";
        }
        
        final String finalName = normalizedName;
        String mappedImage = (type == CategoryType.CUISINE) ? CUISINE_IMAGES.get(finalName) : CATEGORY_IMAGES.get(finalName);
        
        return recipeCategoryRepository.findByNameAndType(finalName, type)
                .orElseGet(() -> recipeCategoryRepository.save(RecipeCategory.builder()
                        .name(finalName)
                        .type(type)
                        .image(mappedImage)
                        .build()));
    }

    @Override
    @Transactional
    public void migrateExistingRecipes() {
        log.info("migrateExistingRecipes is disabled by user request. No database modifications allowed.");
    }

    @Override
    @Transactional
    public void mergeDuplicateTaxonomies() {
        log.info("mergeDuplicateTaxonomies is disabled by user request. No database modifications allowed.");
    }

    @Override
    public Map<String, String> getCategoryImages() { return CATEGORY_IMAGES; }

    @Override
    public Map<String, String> getCuisineImages() { return CUISINE_IMAGES; }
}
