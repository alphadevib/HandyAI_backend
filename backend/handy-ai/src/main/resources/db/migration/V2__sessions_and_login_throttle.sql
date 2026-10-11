-- Signing out bumps a user's token version, which invalidates every token issued before it.
alter table users add column token_version integer not null default 0;

-- Failed sign-in attempts, kept in the database so a restart does not reset the lockout.
-- The key is "email|client address", so one attacker cannot lock an account for everyone.
create table login_throttle (
    throttle_key varchar(220) not null,
    failures integer not null,
    first_failure datetime(6) not null,
    primary key (throttle_key)
) engine=InnoDB;
