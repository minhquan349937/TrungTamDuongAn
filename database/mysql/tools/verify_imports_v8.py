"""Write a transaction-only import verification script to target/ (never commit data)."""
from pathlib import Path
from convert_catalog_v8 import ROOT, OUT, read_source, sql

def main():
    chunks=[]
    for label in ['denver','vb']:
        text=(OUT/f'{label}_v8.sql').read_text(encoding='utf-8')
        chunks.append('\n'.join(line for line in text.splitlines() if line.strip() not in ['START TRANSACTION;','COMMIT;']))
    script='''-- Must run only on the verified EMPTY development duong_an_v11.
SET NAMES utf8mb4;
USE duong_an_v11;
CREATE TEMPORARY TABLE v8_import_checks(ok INT NOT NULL CHECK(ok=1));
START TRANSACTION;
INSERT INTO v8_import_checks SELECT IF(COUNT(*)=0,1,0) FROM programs;
'''+ '\n'.join(chunks)
    for table,count in [('programs',2),('program_levels',7),('domains',26),('program_level_domains',69),('goals',615),('specific_goals',738)]:
        script+=f'\nINSERT INTO v8_import_checks SELECT IF(COUNT(*)={count},1,0) FROM {table};'
    for program,count in [('PR0001',10),('PR0002',16)]:
        script+=f"\nINSERT INTO v8_import_checks SELECT IF(COUNT(*)={count},1,0) FROM domains WHERE program_id='{program}';"
    script+='''
INSERT INTO v8_import_checks SELECT IF(COUNT(*)=0,1,0) FROM program_level_domains ld JOIN program_levels pl ON pl.id=ld.program_level_id JOIN domains d ON d.id=ld.domain_id WHERE pl.program_id<>d.program_id;
INSERT INTO v8_import_checks SELECT IF(COUNT(*)=0,1,0) FROM goals g LEFT JOIN program_level_domains ld ON ld.id=g.level_domain_id WHERE ld.id IS NULL;
'''
    # Independent verification against the OLD source texts, not the converted values.
    for source in ['database/mysql/denver_insert_mysql_v6_1.sql','database/IMPORT_VB_SUA_LOI_1175.sql']:
        rows=read_source(ROOT/source)
        for table,fields in [('goals',['code','name','description']),('specific_goals',['goal_id','code','description','age_min_months','age_max_months'])]:
            for row in rows.get(table,[]):
                conditions=[]
                for field in fields:
                    value=row.get(field)
                    conditions.append(f'CAST({field} AS BINARY) <=> CAST({sql(value)} AS BINARY)')
                script+=f"\nINSERT INTO v8_import_checks SELECT IF(COUNT(*)=1,1,0) FROM {table} WHERE id={sql(row['id'])} AND "+' AND '.join('('+c+')' for c in conditions)+';'
    script+='\nSELECT CONCAT(\'V8_IMPORT_CHECKS_PASSED=\',COUNT(*)) FROM v8_import_checks;\nROLLBACK;\nSELECT CONCAT(\'REMAINING_PROGRAMS=\',COUNT(*)) FROM programs;\nDROP TEMPORARY TABLE v8_import_checks;\n'
    path=ROOT/'target/v8_verify_imports.sql';path.parent.mkdir(exist_ok=True);path.write_text(script,encoding='utf-8')
    print(path)

if __name__=='__main__':main()
