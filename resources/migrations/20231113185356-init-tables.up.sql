CREATE TABLE
  IF NOT EXISTS atlassian_tenants (
    id integer primary key autoincrement,
    key text,
    tenant_name text,
    account_id text,
    client_key text,
    shared_secret text,
    base_url text,
    base_url_short text,
    display_url text,
    product_type text,
    description text,
    service_entitlement_number text,
    oauth_client_id text,
    valid_license integer,
    is_evaluation integer,
    created_at integer default (unixepoch('now'))
  ) STRICT;

--;;
CREATE TABLE
  IF NOT EXISTS slack_teams (
    id integer primary key autoincrement,
    atlassian_tenant_id integer REFERENCES atlassian_tenants ON DELETE CASCADE,
    app_id text,
    external_team_id text,
    team_name text,
    registering_user text,
    scopes text,
    access_token text,
    bot_user_id text,
    created_at integer default (unixepoch('now'))
  ) STRICT;

--;;
CREATE TABLE
  IF NOT EXISTS jobs (
    id integer primary key autoincrement,
    slack_team_id integer NOT NULL REFERENCES slack_teams ON DELETE CASCADE,
    slack_channel_id text NOT NULL,
    owner_slack_user_id text NOT NULL,
    timezone text NOT NULL,
    frequency text NOT NULL,
    target text NOT NULL,
    target_url text,
    last_slack_conversation_datetime integer,
    last_slack_conversation_ts text,
    due_date integer,
    n_runs integer,
    updated_at integer,
    created_at integer default (unixepoch('now')),
    CHECK (frequency in ('once', 'daily', 'weekly'))
  ) STRICT;
