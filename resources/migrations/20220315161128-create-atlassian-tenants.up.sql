CREATE TABLE IF NOT EXISTS atlassian_tenants (
	id serial primary key,
	uuid uuid default uuid_generate_v4(),
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
