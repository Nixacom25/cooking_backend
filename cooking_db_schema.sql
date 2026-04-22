--
-- PostgreSQL database dump
--

\restrict Xdm0bqNdQkJiNbOmW1cxzoGestIM3SBTJwHHVrpGcJo9OYDmiICch9OUKbvPF1A

-- Dumped from database version 14.13 (Ubuntu 14.13-0ubuntu0.22.04.1)
-- Dumped by pg_dump version 16.13 (Ubuntu 16.13-0ubuntu0.24.04.1)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: postgres
--

-- *not* creating schema, since initdb creates it


ALTER SCHEMA public OWNER TO postgres;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activities; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.activities (
    created_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    message character varying(1000) NOT NULL,
    title character varying(255) NOT NULL
);


ALTER TABLE public.activities OWNER TO diedhiouousseynou53;

--
-- Name: blacklisted_tokens; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.blacklisted_tokens (
    blacklisted_at timestamp(6) without time zone NOT NULL,
    id bigint NOT NULL,
    token character varying(500) NOT NULL
);


ALTER TABLE public.blacklisted_tokens OWNER TO diedhiouousseynou53;

--
-- Name: blacklisted_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: diedhiouousseynou53
--

CREATE SEQUENCE public.blacklisted_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.blacklisted_tokens_id_seq OWNER TO diedhiouousseynou53;

--
-- Name: blacklisted_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: diedhiouousseynou53
--

ALTER SEQUENCE public.blacklisted_tokens_id_seq OWNED BY public.blacklisted_tokens.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.categories (
    id bigint NOT NULL,
    name character varying(255) NOT NULL
);


ALTER TABLE public.categories OWNER TO diedhiouousseynou53;

--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: diedhiouousseynou53
--

CREATE SEQUENCE public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.categories_id_seq OWNER TO diedhiouousseynou53;

--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: diedhiouousseynou53
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: cookbook_recipes; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.cookbook_recipes (
    cookbook_id uuid NOT NULL,
    recipe_id uuid NOT NULL
);


ALTER TABLE public.cookbook_recipes OWNER TO diedhiouousseynou53;

--
-- Name: cookbooks; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.cookbooks (
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    name character varying(255) NOT NULL
);


ALTER TABLE public.cookbooks OWNER TO diedhiouousseynou53;

--
-- Name: device_sessions; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.device_sessions (
    id uuid NOT NULL,
    device_name character varying(255) NOT NULL,
    ip_address character varying(255),
    last_active timestamp(6) without time zone,
    location character varying(255),
    token character varying(1000) NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.device_sessions OWNER TO diedhiouousseynou53;

--
-- Name: favorite_recipes; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.favorite_recipes (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    recipe_id uuid NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.favorite_recipes OWNER TO diedhiouousseynou53;

--
-- Name: grocery_items; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.grocery_items (
    is_bought boolean NOT NULL,
    planned_date date,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    recipe_id uuid,
    user_id uuid NOT NULL,
    quantity character varying(255) NOT NULL
);


ALTER TABLE public.grocery_items OWNER TO diedhiouousseynou53;

--
-- Name: ingredients; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.ingredients (
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    icon character varying(255),
    name character varying(255) NOT NULL
);


ALTER TABLE public.ingredients OWNER TO diedhiouousseynou53;

--
-- Name: meal_plans; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.meal_plans (
    planned_date date NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    recipe_id uuid NOT NULL,
    user_id uuid NOT NULL,
    meal_type character varying(255) NOT NULL,
    CONSTRAINT meal_plans_meal_type_check CHECK (((meal_type)::text = ANY ((ARRAY['BREAKFAST'::character varying, 'LUNCH'::character varying, 'DINNER'::character varying, 'SNACK'::character varying, 'EVENT'::character varying])::text[])))
);


ALTER TABLE public.meal_plans OWNER TO diedhiouousseynou53;

--
-- Name: recipe_data; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.recipe_data (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    image_url character varying(255),
    name character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone
);


ALTER TABLE public.recipe_data OWNER TO diedhiouousseynou53;

--
-- Name: recipe_data_id_seq; Type: SEQUENCE; Schema: public; Owner: diedhiouousseynou53
--

CREATE SEQUENCE public.recipe_data_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


ALTER SEQUENCE public.recipe_data_id_seq OWNER TO diedhiouousseynou53;

--
-- Name: recipe_data_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: diedhiouousseynou53
--

ALTER SEQUENCE public.recipe_data_id_seq OWNED BY public.recipe_data.id;


--
-- Name: recipe_ingredients; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.recipe_ingredients (
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    ingredient_id uuid NOT NULL,
    recipe_id uuid NOT NULL,
    quantity character varying(255) NOT NULL
);


ALTER TABLE public.recipe_ingredients OWNER TO diedhiouousseynou53;

--
-- Name: recipe_steps; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.recipe_steps (
    recipe_id uuid NOT NULL,
    step character varying(2000)
);


ALTER TABLE public.recipe_steps OWNER TO diedhiouousseynou53;

--
-- Name: recipes; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.recipes (
    cook_time integer,
    kcal integer,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    image character varying(255),
    name character varying(255) NOT NULL,
    is_public boolean NOT NULL,
    category character varying(255),
    source_url character varying(1000),
    servings integer,
    tips character varying(2000)
);


ALTER TABLE public.recipes OWNER TO diedhiouousseynou53;

--
-- Name: subscription_payments; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.subscription_payments (
    id uuid NOT NULL,
    amount numeric(38,2) NOT NULL,
    created_at timestamp(6) without time zone,
    plan_type character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    stripe_payment_id character varying(255),
    user_id uuid NOT NULL
);


ALTER TABLE public.subscription_payments OWNER TO diedhiouousseynou53;

--
-- Name: subscription_plans; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.subscription_plans (
    monthly_price numeric(38,2) NOT NULL,
    trial_days integer NOT NULL,
    yearly_discount_percentage double precision NOT NULL,
    yearly_price numeric(38,2) NOT NULL,
    created_at timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL
);


ALTER TABLE public.subscription_plans OWNER TO diedhiouousseynou53;

--
-- Name: user_allergies; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_allergies (
    user_id uuid NOT NULL,
    allergy character varying(255)
);


ALTER TABLE public.user_allergies OWNER TO diedhiouousseynou53;

--
-- Name: user_dietary_preferences; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_dietary_preferences (
    user_id uuid NOT NULL,
    preference character varying(255)
);


ALTER TABLE public.user_dietary_preferences OWNER TO diedhiouousseynou53;

--
-- Name: user_favorite_cuisines; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_favorite_cuisines (
    user_id uuid NOT NULL,
    cuisine character varying(255)
);


ALTER TABLE public.user_favorite_cuisines OWNER TO diedhiouousseynou53;

--
-- Name: user_flavor_dna; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_flavor_dna (
    user_id uuid NOT NULL,
    flavor_value integer,
    flavor_key character varying(255) NOT NULL
);


ALTER TABLE public.user_flavor_dna OWNER TO diedhiouousseynou53;

--
-- Name: user_food_dislikes; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_food_dislikes (
    user_id uuid NOT NULL,
    dislike character varying(255)
);


ALTER TABLE public.user_food_dislikes OWNER TO diedhiouousseynou53;

--
-- Name: user_kitchen_appliances; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_kitchen_appliances (
    user_id uuid NOT NULL,
    appliance character varying(255)
);


ALTER TABLE public.user_kitchen_appliances OWNER TO diedhiouousseynou53;

--
-- Name: user_notification_preferences; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_notification_preferences (
    user_id uuid NOT NULL,
    preference character varying(255)
);


ALTER TABLE public.user_notification_preferences OWNER TO diedhiouousseynou53;

--
-- Name: user_onboarding_goals; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_onboarding_goals (
    user_id uuid NOT NULL,
    goal character varying(255)
);


ALTER TABLE public.user_onboarding_goals OWNER TO diedhiouousseynou53;

--
-- Name: user_saved_ingredients; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_saved_ingredients (
    id uuid NOT NULL,
    created_at timestamp(6) without time zone,
    icon character varying(255),
    name character varying(255) NOT NULL,
    user_id uuid NOT NULL
);


ALTER TABLE public.user_saved_ingredients OWNER TO diedhiouousseynou53;

--
-- Name: user_subscriptions; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.user_subscriptions (
    is_yearly boolean NOT NULL,
    created_at timestamp(6) without time zone,
    end_date timestamp(6) without time zone NOT NULL,
    start_date timestamp(6) without time zone NOT NULL,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    status character varying(255) NOT NULL,
    CONSTRAINT user_subscriptions_status_check CHECK (((status)::text = ANY ((ARRAY['TRIAL'::character varying, 'ACTIVE'::character varying, 'EXPIRED'::character varying])::text[])))
);


ALTER TABLE public.user_subscriptions OWNER TO diedhiouousseynou53;

--
-- Name: users; Type: TABLE; Schema: public; Owner: diedhiouousseynou53
--

CREATE TABLE public.users (
    resend_count integer NOT NULL,
    created_at timestamp(6) without time zone,
    deleted_at timestamp(6) without time zone,
    lockout_until timestamp(6) without time zone,
    otp_expiration timestamp(6) without time zone,
    updated_at timestamp(6) without time zone,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    firstname character varying(255) NOT NULL,
    lastname character varying(255) NOT NULL,
    otp_code character varying(255),
    password character varying(255) NOT NULL,
    phone character varying(255) NOT NULL,
    photo character varying(255),
    provider character varying(255) NOT NULL,
    provider_id character varying(255),
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    cooking_frequency character varying(255),
    cooking_skill character varying(255),
    cooking_target character varying(255),
    cooking_time_preference character varying(255),
    discovery_source character varying(255),
    meal_planning_style character varying(255),
    other_discovery_source character varying(255),
    spice_level character varying(255),
    onboarding_feedback text,
    onboarding_rating integer,
    country character varying(255),
    language character varying(255),
    alternative_region character varying(255),
    measurement_system character varying(255),
    CONSTRAINT users_provider_check CHECK (((provider)::text = ANY ((ARRAY['LOCAL'::character varying, 'GOOGLE'::character varying, 'APPLE'::character varying])::text[]))),
    CONSTRAINT users_role_check CHECK (((role)::text = ANY ((ARRAY['CLIENT'::character varying, 'ADMIN'::character varying])::text[]))),
    CONSTRAINT users_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING_VERIFICATION'::character varying, 'ACTIVE'::character varying, 'BLOCKED'::character varying, 'ARCHIVED'::character varying])::text[])))
);


ALTER TABLE public.users OWNER TO diedhiouousseynou53;

--
-- Name: blacklisted_tokens id; Type: DEFAULT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.blacklisted_tokens ALTER COLUMN id SET DEFAULT nextval('public.blacklisted_tokens_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: recipe_data id; Type: DEFAULT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_data ALTER COLUMN id SET DEFAULT nextval('public.recipe_data_id_seq'::regclass);


--
-- Name: activities activities_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT activities_pkey PRIMARY KEY (id);


--
-- Name: blacklisted_tokens blacklisted_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT blacklisted_tokens_pkey PRIMARY KEY (id);


--
-- Name: blacklisted_tokens blacklisted_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.blacklisted_tokens
    ADD CONSTRAINT blacklisted_tokens_token_key UNIQUE (token);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: cookbook_recipes cookbook_recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbook_recipes
    ADD CONSTRAINT cookbook_recipes_pkey PRIMARY KEY (cookbook_id, recipe_id);


--
-- Name: cookbooks cookbooks_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbooks
    ADD CONSTRAINT cookbooks_pkey PRIMARY KEY (id);


--
-- Name: cookbooks cookbooks_user_id_name_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbooks
    ADD CONSTRAINT cookbooks_user_id_name_key UNIQUE (user_id, name);


--
-- Name: device_sessions device_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_pkey PRIMARY KEY (id);


--
-- Name: favorite_recipes favorite_recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.favorite_recipes
    ADD CONSTRAINT favorite_recipes_pkey PRIMARY KEY (id);


--
-- Name: grocery_items grocery_items_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.grocery_items
    ADD CONSTRAINT grocery_items_pkey PRIMARY KEY (id);


--
-- Name: ingredients ingredients_name_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT ingredients_name_key UNIQUE (name);


--
-- Name: ingredients ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.ingredients
    ADD CONSTRAINT ingredients_pkey PRIMARY KEY (id);


--
-- Name: meal_plans meal_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.meal_plans
    ADD CONSTRAINT meal_plans_pkey PRIMARY KEY (id);


--
-- Name: recipe_data recipe_data_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_data
    ADD CONSTRAINT recipe_data_pkey PRIMARY KEY (id);


--
-- Name: recipe_ingredients recipe_ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT recipe_ingredients_pkey PRIMARY KEY (id);


--
-- Name: recipes recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_pkey PRIMARY KEY (id);


--
-- Name: recipes recipes_user_id_name_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_user_id_name_key UNIQUE (user_id, name);


--
-- Name: subscription_payments subscription_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.subscription_payments
    ADD CONSTRAINT subscription_payments_pkey PRIMARY KEY (id);


--
-- Name: subscription_plans subscription_plans_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.subscription_plans
    ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);


--
-- Name: favorite_recipes uk4lin80624woiba66jl3jqnhfy; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.favorite_recipes
    ADD CONSTRAINT uk4lin80624woiba66jl3jqnhfy UNIQUE (user_id, recipe_id);


--
-- Name: categories uk_t8o6pivur7nn124jehx7cygw5; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT uk_t8o6pivur7nn124jehx7cygw5 UNIQUE (name);


--
-- Name: recipes ukdwxoxveq3mm4ljoumnvngowh7; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT ukdwxoxveq3mm4ljoumnvngowh7 UNIQUE (user_id, name);


--
-- Name: user_saved_ingredients ukj8e1i8mr5kj7btoby5b817gow; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_saved_ingredients
    ADD CONSTRAINT ukj8e1i8mr5kj7btoby5b817gow UNIQUE (user_id, name);


--
-- Name: cookbooks ukr37lo1gdy1dxyslplhx4ddbjj; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbooks
    ADD CONSTRAINT ukr37lo1gdy1dxyslplhx4ddbjj UNIQUE (user_id, name);


--
-- Name: user_flavor_dna user_flavor_dna_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_flavor_dna
    ADD CONSTRAINT user_flavor_dna_pkey PRIMARY KEY (user_id, flavor_key);


--
-- Name: user_saved_ingredients user_saved_ingredients_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_saved_ingredients
    ADD CONSTRAINT user_saved_ingredients_pkey PRIMARY KEY (id);


--
-- Name: user_subscriptions user_subscriptions_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_subscriptions
    ADD CONSTRAINT user_subscriptions_pkey PRIMARY KEY (id);


--
-- Name: user_subscriptions user_subscriptions_user_id_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_subscriptions
    ADD CONSTRAINT user_subscriptions_user_id_key UNIQUE (user_id);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_phone_key; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_phone_key UNIQUE (phone);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: user_subscriptions fk3l40lbyji8kj5xoc20ycwsc8g; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_subscriptions
    ADD CONSTRAINT fk3l40lbyji8kj5xoc20ycwsc8g FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_allergies fk4mclrynvl1em11jh8sxt5s74k; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_allergies
    ADD CONSTRAINT fk4mclrynvl1em11jh8sxt5s74k FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: cookbook_recipes fk4qd84d5ifiuj7vayf0t5nbtau; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbook_recipes
    ADD CONSTRAINT fk4qd84d5ifiuj7vayf0t5nbtau FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: meal_plans fk4ujykkwkhu3jx36605vvgvvy7; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.meal_plans
    ADD CONSTRAINT fk4ujykkwkhu3jx36605vvgvvy7 FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: user_food_dislikes fk4wbe4pymkr76us66wxwx8uy0r; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_food_dislikes
    ADD CONSTRAINT fk4wbe4pymkr76us66wxwx8uy0r FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: cookbook_recipes fk5xfygblidqb6jre8vb3l6nm4j; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbook_recipes
    ADD CONSTRAINT fk5xfygblidqb6jre8vb3l6nm4j FOREIGN KEY (cookbook_id) REFERENCES public.cookbooks(id);


--
-- Name: meal_plans fk7friea1stnx97lswpyxlb9ln1; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.meal_plans
    ADD CONSTRAINT fk7friea1stnx97lswpyxlb9ln1 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: grocery_items fk7t4w8pfbc7b4wb2xonrk1bwgb; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.grocery_items
    ADD CONSTRAINT fk7t4w8pfbc7b4wb2xonrk1bwgb FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id);


--
-- Name: favorite_recipes fk8njt7bkbsjxkp0qgi8jt6h0a9; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.favorite_recipes
    ADD CONSTRAINT fk8njt7bkbsjxkp0qgi8jt6h0a9 FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: user_flavor_dna fk9ox33g3qgarid9eeonlgb6vkq; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_flavor_dna
    ADD CONSTRAINT fk9ox33g3qgarid9eeonlgb6vkq FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_favorite_cuisines fkbcfw00kjnoshh41wcne0kqb7h; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_favorite_cuisines
    ADD CONSTRAINT fkbcfw00kjnoshh41wcne0kqb7h FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipe_ingredients fkcqlw8sor5ut10xsuj3jnttkc; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT fkcqlw8sor5ut10xsuj3jnttkc FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: user_kitchen_appliances fkdfrg7vca4in1oiga9id8ssd6; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_kitchen_appliances
    ADD CONSTRAINT fkdfrg7vca4in1oiga9id8ssd6 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: subscription_payments fkdh0bmmy3aw4i8wrgt7cery9sa; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.subscription_payments
    ADD CONSTRAINT fkdh0bmmy3aw4i8wrgt7cery9sa FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: grocery_items fke3owympmfys02skgbphsg6276; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.grocery_items
    ADD CONSTRAINT fke3owympmfys02skgbphsg6276 FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: recipe_ingredients fkgukrw6na9f61kb8djkkuvyxy8; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_ingredients
    ADD CONSTRAINT fkgukrw6na9f61kb8djkkuvyxy8 FOREIGN KEY (ingredient_id) REFERENCES public.ingredients(id);


--
-- Name: user_saved_ingredients fkj3dwrd6ijgmcttspouks8687j; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_saved_ingredients
    ADD CONSTRAINT fkj3dwrd6ijgmcttspouks8687j FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_notification_preferences fkjqii7bt0v7fyg56obr7nio4ax; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_notification_preferences
    ADD CONSTRAINT fkjqii7bt0v7fyg56obr7nio4ax FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: cookbooks fkkdfe9sqhoore4tqp3xgi7iom3; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.cookbooks
    ADD CONSTRAINT fkkdfe9sqhoore4tqp3xgi7iom3 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_dietary_preferences fkkw0htgeasxnckeafjthrqgk35; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_dietary_preferences
    ADD CONSTRAINT fkkw0htgeasxnckeafjthrqgk35 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipes fklc3x6yty3xsupx80hqbj9ayos; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT fklc3x6yty3xsupx80hqbj9ayos FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: device_sessions fkmaf4t03km2fav72pabwnggdns; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT fkmaf4t03km2fav72pabwnggdns FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: grocery_items fkn88g44l0trmh0h6lthrldrrge; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.grocery_items
    ADD CONSTRAINT fkn88g44l0trmh0h6lthrldrrge FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: favorite_recipes fkn8jb5jyh08bh9eijok9hr2pdh; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.favorite_recipes
    ADD CONSTRAINT fkn8jb5jyh08bh9eijok9hr2pdh FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: user_onboarding_goals fknlr9h7uqxgdxsqljwpc7c5gpe; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.user_onboarding_goals
    ADD CONSTRAINT fknlr9h7uqxgdxsqljwpc7c5gpe FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: recipe_steps fkof4i3g3aiwgro5ykaf1j28iw1; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.recipe_steps
    ADD CONSTRAINT fkof4i3g3aiwgro5ykaf1j28iw1 FOREIGN KEY (recipe_id) REFERENCES public.recipes(id);


--
-- Name: activities fkq6cjukylkgxdjkm9npk9va2f2; Type: FK CONSTRAINT; Schema: public; Owner: diedhiouousseynou53
--

ALTER TABLE ONLY public.activities
    ADD CONSTRAINT fkq6cjukylkgxdjkm9npk9va2f2 FOREIGN KEY (user_id) REFERENCES public.users(id);


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: postgres
--

REVOKE USAGE ON SCHEMA public FROM PUBLIC;
GRANT ALL ON SCHEMA public TO PUBLIC;


--
-- PostgreSQL database dump complete
--

\unrestrict Xdm0bqNdQkJiNbOmW1cxzoGestIM3SBTJwHHVrpGcJo9OYDmiICch9OUKbvPF1A

