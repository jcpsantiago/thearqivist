CREATE TABLE
  IF NOT EXISTS atlassian_tenants (
    id serial primary key,
    key varchar(255),
    tenant_name varchar(255),
    account_id varchar(255),
    client_key varchar(255),
    shared_secret varchar(255),
    base_url varchar(255),
    base_url_short varchar(255),
    display_url varchar(255),
    product_type varchar(255),
    description varchar(255),
    service_entitlement_number varchar(255),
    oauth_client_id varchar(255),
    valid_license boolean,
    is_evaluation boolean,
    created_at timestamp default current_timestamp
  );

--;;
CREATE TABLE
  IF NOT EXISTS slack_teams (
    id serial primary key,
    atlassian_tenant_id int REFERENCES atlassian_tenants ON DELETE CASCADE,
    app_id varchar(255),
    external_team_id varchar(255),
    team_name varchar(255),
    registering_user varchar(255),
    scopes varchar(255),
    access_token varchar(255),
    bot_user_id varchar(255),
    created_at timestamp default current_timestamp
  );

--;;
CREATE TABLE
  IF NOT EXISTS recurrent_jobs (
    id serial primary key,
    slack_team_id int REFERENCES slack_teams ON DELETE CASCADE,
    slack_channel_id varchar(255),
    creator_slack_user_id varchar(255),
    frequency varchar(255),
    last_slack_conversation_ts varchar(255),
    due_date timestamp,
    created_at timestamp default current_timestamp
  );
