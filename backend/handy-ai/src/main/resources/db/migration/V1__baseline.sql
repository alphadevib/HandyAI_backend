-- The schema as it stood when migrations were introduced (generated from the entities with
-- Hibernate's MySQL dialect). Databases that already have these tables are baselined at this
-- version and skip it; new databases start here.

create table ai_tools (
    featured bit not null,
    popularity integer not null,
    price_annual_usd float(53),
    price_monthly_usd float(53),
    rating_count integer not null,
    rating_sum integer not null,
    category_id bigint not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    price_annual_inr bigint,
    price_monthly_inr bigint,
    name varchar(120) not null,
    price_note varchar(120),
    slug varchar(140) not null,
    tagline varchar(200) not null,
    tags varchar(400),
    website_url varchar(400) not null,
    description varchar(2000) not null,
    pricing_model enum ('FREE','FREEMIUM','PAID','TRIAL') not null,
    primary key (id)
) engine=InnoDB;;

create table categories (
    icon varchar(8),
    id bigint not null auto_increment,
    name varchar(80) not null,
    slug varchar(80) not null,
    description varchar(300),
    primary key (id)
) engine=InnoDB;;

create table favorites (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    tool_id bigint not null,
    user_id bigint not null,
    primary key (id)
) engine=InnoDB;;

create table outbound_clicks (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    tool_id bigint not null,
    user_id bigint,
    source varchar(20),
    primary key (id)
) engine=InnoDB;;

create table reviews (
    rating integer not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    tool_id bigint not null,
    user_id bigint not null,
    comment varchar(1200),
    primary key (id)
) engine=InnoDB;;

create table subscriptions (
    amount float(53) not null,
    start_date date not null,
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    tool_id bigint not null,
    user_id bigint not null,
    notes varchar(300),
    billing_cycle enum ('ANNUAL','MONTHLY','QUARTERLY') not null,
    currency enum ('INR','USD') not null,
    status enum ('ACTIVE','CANCELLED') not null,
    primary key (id)
) engine=InnoDB;;

create table suggestions (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    user_id bigint,
    contact_name varchar(80),
    page varchar(120),
    contact_email varchar(160),
    message varchar(1000) not null,
    category enum ('BUG','IDEA','OTHER','TOOL_REQUEST') not null,
    status enum ('DISMISSED','DONE','NEW','PLANNED') not null,
    primary key (id)
) engine=InnoDB;;

create table users (
    created_at datetime(6) not null,
    id bigint not null auto_increment,
    verification_submitted_at datetime(6),
    organisation_registration_id varchar(40),
    name varchar(80) not null,
    profession varchar(120),
    email varchar(160) not null,
    organisation_name varchar(160),
    organisation_website varchar(200),
    verification_note varchar(300),
    password_hash varchar(255) not null,
    account_type enum ('INDIVIDUAL','ORGANISATION'),
    role enum ('ADMIN','USER') not null,
    verification_status enum ('NOT_REQUIRED','PENDING','REJECTED','VERIFIED'),
    primary key (id)
) engine=InnoDB;;

create index idx_ai_tools_category 
   on ai_tools (category_id);;

alter table ai_tools 
   add constraint idx_ai_tools_slug unique (slug);;

alter table categories 
   add constraint UKt8o6pivur7nn124jehx7cygw5 unique (name);;

alter table categories 
   add constraint UKoul14ho7bctbefv8jywp5v3i2 unique (slug);;

alter table favorites 
   add constraint uk_favorite_user_tool unique (user_id, tool_id);;

create index idx_outbound_created 
   on outbound_clicks (created_at);;

create index idx_outbound_tool 
   on outbound_clicks (tool_id);;

alter table reviews 
   add constraint uk_review_user_tool unique (user_id, tool_id);;

alter table subscriptions 
   add constraint uk_subscription_user_tool unique (user_id, tool_id);;

create index idx_suggestion_status 
   on suggestions (status);;

alter table users 
   add constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email);;

alter table ai_tools 
   add constraint FKmmisdx6ctgy5e61xonk43fxes 
   foreign key (category_id) 
   references categories (id);;

alter table favorites 
   add constraint FKn5gtxshhdv1m7ungd8x9rvuhy 
   foreign key (tool_id) 
   references ai_tools (id);;

alter table favorites 
   add constraint FKk7du8b8ewipawnnpg76d55fus 
   foreign key (user_id) 
   references users (id);;

alter table outbound_clicks 
   add constraint FKrd73e3uj9hpxnydw9fs8rkul 
   foreign key (tool_id) 
   references ai_tools (id);;

alter table reviews 
   add constraint FKcdb6xn2gt9cululuo58yfh6kf 
   foreign key (tool_id) 
   references ai_tools (id);;

alter table reviews 
   add constraint FKcgy7qjc1r99dp117y9en6lxye 
   foreign key (user_id) 
   references users (id);;

alter table subscriptions 
   add constraint FK30vt1fxapa8gf10gtjl23w384 
   foreign key (tool_id) 
   references ai_tools (id);;

alter table subscriptions 
   add constraint FKhro52ohfqfbay9774bev0qinr 
   foreign key (user_id) 
   references users (id);;
