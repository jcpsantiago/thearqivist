CREATE TABLE IF NOT EXISTS integrations(
	id serial primary key,
	uuid uuid default uuid_generate_v4(),
	slack_team_id int REFERENCES slack_teams(id) ON DELETE CASCADE,
	atlassian_tenant_id int REFERENCES atlassian_tenants(id) ON DELETE CASCADE,
	created_at timestamp default current_timestamp
);
