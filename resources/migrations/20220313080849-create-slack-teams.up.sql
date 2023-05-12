CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
--;;
create table if not exists slack_teams (
	id serial primary key,
	uuid uuid default uuid_generate_v4(),
	app_id varchar(255),
	external_team_id varchar(255),
	team_name varchar(255),
	registering_user varchar(255),
	scopes varchar(255),
	access_token varchar(255),
	bot_user_id varchar(255),
	created_at timestamp default current_timestamp
);
