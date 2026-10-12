-- Categories a user follows: the join table behind User.interests (@ManyToMany).
create table user_interests (
    user_id bigint not null,
    category_id bigint not null,
    primary key (user_id, category_id),
    constraint fk_user_interests_user foreign key (user_id) references users (id) on delete cascade,
    constraint fk_user_interests_category foreign key (category_id) references categories (id)
        on delete cascade
) engine=InnoDB;

-- Each user's Claude usage, one row per user sharing its primary key (UserAiUsage, @OneToOne).
create table user_ai_usage (
    user_id bigint not null,
    calls bigint not null,
    input_tokens bigint not null,
    output_tokens bigint not null,
    cost_usd float(53) not null,
    last_used_at datetime(6),
    primary key (user_id),
    constraint fk_user_ai_usage_user foreign key (user_id) references users (id) on delete cascade
) engine=InnoDB;
