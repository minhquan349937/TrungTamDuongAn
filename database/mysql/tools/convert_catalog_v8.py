"""Convert the two checked-in real sources to V8 without executing legacy INSERTs.

Only INSERT ... VALUES on catalog tables is read. Original codes, names and
descriptions are preserved. Run from any directory with Python 3.
"""
import csv
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / 'database/mysql/imports/v8'

def parse_values(body):
    rows=[]; i=0
    while i<len(body):
        if body[i]!='(': i+=1; continue
        row=[]; i+=1
        while True:
            while body[i].isspace(): i+=1
            if body[i]=="'":
                i+=1; value=''
                while True:
                    c=body[i]; i+=1
                    if c=="'":
                        if i<len(body) and body[i]=="'":value+="'";i+=1;continue
                        break
                    if c=='\\':
                        c=body[i];i+=1;c={'n':'\n','r':'\r','t':'\t','0':'\0'}.get(c,c)
                    value+=c
            else:
                start=i
                while body[i] not in ',)':i+=1
                token=body[start:i].strip();value=None if token.upper()=='NULL' else int(token)
            row.append(value)
            while body[i].isspace():i+=1
            if body[i]==')':i+=1;break
            assert body[i]==',';i+=1
        rows.append(row)
    return rows

def read_source(path):
    s=path.read_text(encoding='utf-8-sig'); result={}
    # Match end-of-statement outside strings (including semicolons inside text).
    pattern=r"INSERT\s+INTO\s+`?(programs|program_levels|domains|goals|specific_goals)`?\s*\(([^)]+)\)\s*VALUES\s*"
    for m in re.finditer(pattern,s,re.I):
        i=m.end();start=i;quoted=False
        while i<len(s):
            if s[i]=='\\' and quoted:i+=2;continue
            if s[i]=="'":
                if quoted and i+1<len(s) and s[i+1]=="'":i+=2;continue
                quoted=not quoted
            elif s[i]==';' and not quoted:break
            i+=1
        cols=[c.strip().strip('`') for c in m[2].split(',')]
        result.setdefault(m[1].lower(),[]).extend(dict(zip(cols,r)) for r in parse_values(s[start:i]))
    return result

def normalized(s):
    return ''.join(c for c in unicodedata.normalize('NFD',s.lower()) if not unicodedata.combining(c)).replace('đ','d')

DENVER=[('GTN','Giao tiếp tiếp nhận'),('GTD','Giao tiếp diễn đạt'),('XH','Kỹ năng xã hội'),
        ('BC','Bắt chước'),('NT','Nhận thức'),('C','Chơi'),('VDT','Vận động tinh'),
        ('VDTH','Vận động thô'),('HV','Hành vi'),('TL','Tự lập')]

def denver_domain(name):
    name=normalized(name)
    if name.startswith('giao tiep tiep nhan'):return 0
    if name.startswith('giao tiep dien dat'):return 1
    if 'xa hoi' in name or 'chu y chung' in name:return 2
    if 'bat chuoc' in name:return 3
    if 'nhan thuc' in name:return 4
    if name.startswith('choi'):return 5
    if 'van dong tinh' in name:return 6
    if 'van dong tho' in name:return 7
    if name=='hanh vi':return 8
    if name.startswith('tu lap'):return 9
    raise ValueError('Unmapped real Denver domain: '+name)

def sql(v):
    if v is None:return 'NULL'
    if isinstance(v,int):return str(v)
    return "'"+v.replace('\\','\\\\').replace("'","''").replace('\0','\\0')+"'"

def convert(source,label,offset):
    data=read_source(ROOT/source);program=data['programs'][0]['id']
    domains={};links={};mapping={};provenance=[]
    for old in data['domains']:
        code,name=DENVER[denver_domain(old['name'])] if label=='denver' else (old['code'],old['name'])
        if code not in domains:
            domains[code]=dict(id=f'DM{offset+len(domains)+1:04}',program_id=program,code=code,name=name,
                               description=None,is_active=1,display_order=len(domains)+1)
        d=domains[code];key=(old['program_level_id'],d['id'])
        if key not in links:
            links[key]=dict(id=f'LD{offset+len(links)+1:04}',program_id=program,program_level_id=key[0],domain_id=key[1],display_order=d['display_order'])
        mapping[old['id']]=links[key]['id']
        provenance.append([old['id'],old['name'],d['id'],name,links[key]['id']])
    counts={};seen=set()
    for goal in data['goals']:
        ld=mapping[goal.pop('domain_id')];goal['level_domain_id']=ld
        key=(ld,goal['code']);assert key not in seen,key;seen.add(key)
        counts[ld]=counts.get(ld,0)+1;goal['display_order']=counts[ld]
    data['domains']=list(domains.values());data['program_level_domains']=list(links.values())
    tables=['programs','program_levels','domains','program_level_domains','goals','specific_goals']
    content='-- V8 N-N import from real source: '+source+'\n-- Execute the WHOLE file; on any error ROLLBACK immediately. Stop catalog writers during import.\nSET NAMES utf8mb4;\nUSE duong_an_v11;\nSTART TRANSACTION;\n'
    for table in tables:
        rows=data.get(table,[])
        maximum=max([int(r['id'][2:]) for r in rows]+[0])+1
        content+=f"UPDATE catalog_id_sequences SET next_value=GREATEST(next_value,{maximum}) WHERE entity_name='{table}';\n"
    for table in tables:
        rows=data.get(table,[])
        if not rows:continue
        columns=list(rows[0]);content+=f'\n-- {table}: {len(rows)}\n'
        for start in range(0,len(rows),100):
            content+='INSERT INTO '+table+' ('+','.join(columns)+') VALUES\n'
            content+=',\n'.join(' ('+','.join(sql(r.get(c)) for c in columns)+')' for r in rows[start:start+100])+';\n'
    content+='COMMIT;\n'
    OUT.mkdir(parents=True,exist_ok=True)
    (OUT/f'{label}_v8.sql').write_text(content,encoding='utf-8')
    with (OUT/f'{label}_domain_mapping.csv').open('w',encoding='utf-8-sig',newline='') as f:
        writer=csv.writer(f);writer.writerow(['source_domain_id','source_section','domain_id','domain_name','level_domain_id']);writer.writerows(provenance)
    print(label,{table:len(data.get(table,[])) for table in tables})

if __name__=='__main__':
    convert('database/mysql/denver_insert_mysql_v6_1.sql','denver',0)
    convert('database/IMPORT_VB_SUA_LOI_1175.sql','vb',5000)
