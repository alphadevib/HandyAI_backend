-- New sign-ups confirm their email before they can sign in. Accounts that already exist are
-- treated as confirmed (default 1), so nobody is locked out by this change.
alter table users add column email_verified bit not null default 1;
-- SHA-256 of the emailed one-time token (the token itself is never stored) and when it was sent.
alter table users add column email_token_hash varchar(64);
alter table users add column email_token_sent_at datetime(6);
create index idx_users_email_token on users (email_token_hash);
