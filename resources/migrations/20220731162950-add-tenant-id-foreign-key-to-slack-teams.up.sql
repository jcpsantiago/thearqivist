ALTER TABLE slack_teams
ADD COLUMN atlassian_tenant_id int REFERENCES atlassian_tenants(id) ON DELETE CASCADE;
