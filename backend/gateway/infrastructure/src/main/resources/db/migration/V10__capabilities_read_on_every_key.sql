update api_keys
set scopes = trim(scopes || ' capabilities:read')
where ' ' || scopes || ' ' not like '% capabilities:read %';
