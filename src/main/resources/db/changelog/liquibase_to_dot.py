import os
import xml.etree.ElementTree as ET

# Директория с XML-файлами миграций (измените на вашу)
MIGRATION_DIR = 'changes'  # Пример пути; укажите свой

# NS для Liquibase XML
NS = '{http://www.liquibase.org/xml/ns/dbchangelog}'

tables = {}  # {table_name: [{'name': col, 'type': type, 'pk': bool, 'unique': bool, 'nullable': bool}]}
relations = []  # [('from_table:from_col', 'to_table:to_col')]

# Парсинг всех XML в директории
for filename in os.listdir(MIGRATION_DIR):
    if filename.endswith('.xml'):
        tree = ET.parse(os.path.join(MIGRATION_DIR, filename))
        root = tree.getroot()

        # Парсинг таблиц
        for changeset in root.findall(f'.//{NS}changeSet'):
            for create_table in changeset.findall(f'{NS}createTable'):
                table_name = create_table.attrib['tableName']
                columns = []
                for column in create_table.findall(f'{NS}column'):
                    col_name = column.attrib['name']
                    col_type = column.attrib['type']
                    constraints = column.find(f'{NS}constraints')
                    pk = constraints.attrib.get('primaryKey', 'false') == 'true' if constraints else False
                    unique = constraints.attrib.get('unique', 'false') == 'true' if constraints else False
                    nullable = constraints.attrib.get('nullable', 'true') == 'true' if constraints else True
                    columns.append({
                        'name': col_name,
                        'type': col_type,
                        'pk': pk,
                        'unique': unique,
                        'nullable': nullable
                    })
                tables[table_name] = columns

            # Парсинг FK (связи)
            for add_fk in changeset.findall(f'{NS}addForeignKeyConstraint'):
                from_table = add_fk.attrib.get('baseTableName')
                from_col = add_fk.attrib.get('baseColumnNames')
                to_table = add_fk.attrib.get('referencedTableName')
                to_col = add_fk.attrib.get('referencedColumnNames')
                if from_table and to_table:
                    relations.append((f'{from_table}:{from_col}', f'{to_table}:{to_col}'))

# Генерация DOT
dot = 'digraph ER {\n'
dot += '    node [shape=plaintext];\n'
dot += '    rankdir=LR;  // Горизонтальная ориентация\n'

for table, cols in tables.items():
    dot += f'    {table} [label=<\n'
    dot += f'        <table border="0" cellborder="1" cellspacing="0">\n'
    dot += f'            <tr><td><b>{table}</b></td></tr>\n'
    for col in cols:
        attrs = []
        if col['pk']: attrs.append('PK')
        if col['unique']: attrs.append('UNIQUE')
        if not col['nullable']: attrs.append('NOT NULL')
        attr_str = f' ({", ".join(attrs)})' if attrs else ''
        dot += f'            <tr><td port="{col["name"]}">{col["name"]}: {col["type"]}{attr_str}</td></tr>\n'
    dot += '        </table>\n'
    dot += '    >];\n'

# Связи (стрелки между таблицами)
for from_ref, to_ref in relations:
    dot += f'    {from_ref} -> {to_ref};\n'

dot += '}\n'

# Сохранение в файл
with open('db_schema.dot', 'w') as f:
    f.write(dot)

print('DOT файл сохранён как db_schema.dot')