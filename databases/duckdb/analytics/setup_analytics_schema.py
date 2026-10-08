from pathlib import Path
import duckdb


ROOT_DIR = Path(__file__).resolve().parents[1]

DB_PATH = ROOT_DIR / "analytics.duckdb"
SCHEMA_PATH = ROOT_DIR / "contracts" / "analytics-schema.sql"


def main():
    connection = duckdb.connect(str(DB_PATH))

    schema = SCHEMA_PATH.read_text()
    connection.execute(schema)

    connection.close()

    print("Analytics schema created successfully.")


if __name__ == "__main__":
    main()