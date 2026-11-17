update api_keys k
set principal_id = p.kept
from (select id, first_value(id) over (partition by name order by created_at, id) as kept from principals) p
where k.principal_id = p.id and p.id <> p.kept;

delete from principals p
using principals q
where p.name = q.name and (p.created_at, p.id) > (q.created_at, q.id);

alter table principals add constraint principals_name_key unique (name);
