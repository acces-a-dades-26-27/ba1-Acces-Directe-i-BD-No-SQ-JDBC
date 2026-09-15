# BA1 - JDBC: de la connexió als PreparedStatements

Aquest document explica, pas a pas, el projecte `ba1_jdbc` i quins conceptes es volen
treballar amb cada classe. L'ordre de lectura recomanat per a l'alumnat és:

1. `pom.xml` (el connector)
2. `dbConn.java` (connexió + SELECT bàsic)
3. `dbInsert.java` (DDL + INSERT + recórrer un ResultSet)
4. `AlterTableErrors.java` (try-with-resources i jerarquia d'excepcions)
5. `PreparedStatements.java` (primer contacte amb consultes parametritzades)
6. `PreparedStatementsAvancat.java` (injecció SQL, dates, LIKE, BETWEEN, WHERE dinàmic)

Base de dades de treball: `house`, taula `kitchen`.

---

## 0. Requisits previs

Abans d'executar res, cal tenir a MySQL:

```sql
CREATE DATABASE house;
```

La taula `kitchen` la crea el mateix codi (`dbInsert`), no cal crear-la a mà.

Dades de connexió que fa servir el projecte:

- URL: `jdbc:mysql://localhost:3306/house`
- Usuari: `root`
- Contrasenya: `super3`

Punt a comentar a classe: tenir les credencials escrites dins del codi és còmode per a un
exemple, però a la vida real van en un fitxer de configuració (`.properties`) o en variables
d'entorn, fora del repositori.

---

## 1. El connector: `pom.xml`

JDBC és una API estàndard de Java (`java.sql`), però no sap parlar amb MySQL pel seu compte.
Necessita un **driver**, que és una llibreria externa. Maven s'encarrega de descarregar-la.

```xml
<dependencies>
  <dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.4.0</version>
  </dependency>
</dependencies>
```

Què han d'entendre:

- `groupId` + `artifactId` + `version` identifiquen de manera única la llibreria.
- Maven la baixa del repositori central i la deixa al repositori local (`~/.m2/repository`).
- A partir d'aquí, les classes del driver ja són al classpath i es poden fer servir.
- L'API (`Connection`, `Statement`, `ResultSet`...) és sempre la mateixa; només canviant el
  driver i la URL podríem atacar PostgreSQL, Oracle o SQLite. Aquesta és la gràcia de JDBC.

També s'hi defineix la codificació del projecte:

```xml
<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
```

Important quan es fan servir accents i el símbol `m²` als `System.out.println`.

---

## 2. `dbConn.java` - connectar-se i llegir

És l'exemple mínim. Fa quatre coses:

### 2.1. Carregar el driver

```java
Class.forName("com.mysql.cj.jdbc.Driver");
```

Carrega la classe del driver i, en carregar-se, aquesta es registra al `DriverManager`.

Matís que val la pena explicar: **des de JDBC 4.0 aquesta línia ja no és necessària**. El driver
es registra automàticament (mecanisme `ServiceLoader`, fitxer `META-INF/services`). Es manté
perquè surt a molts manuals i perquè obliga a capturar `ClassNotFoundException`, que és una
excepció diferent de les de base de dades. A `AlterTableErrors` i a `PreparedStatements` ja no hi és.

### 2.2. Obtenir la connexió

```java
Connection connection = DriverManager.getConnection(url, user, password);
```

`DriverManager` mira la URL, tria el driver adequat i retorna un objecte `Connection`, que
representa la sessió oberta amb el servidor.

### 2.3. Executar la consulta

```java
Statement statement = connection.createStatement();
ResultSet resultSet = statement.executeQuery("SHOW DATABASES");
```

Els tres objectes clau de JDBC, en ordre:

| Objecte | Què és | Qui el crea |
|---|---|---|
| `Connection` | la sessió amb la BD | `DriverManager` |
| `Statement` | l'ordre SQL a executar | `Connection` |
| `ResultSet` | la taula de resultats | `Statement` |

Dos mètodes que **no** són intercanviables:

- `executeQuery(sql)` -> retorna `ResultSet`. Per a `SELECT`, `SHOW`.
- `executeUpdate(sql)` -> retorna un `int`. Per a `INSERT`, `UPDATE`, `DELETE`
  (nombre de files afectades) i també per a DDL com `CREATE` o `ALTER` (retorna 0).

### 2.4. Recórrer el resultat

```java
while (resultSet.next()) {
    System.out.println(resultSet.getString(1));
}
```

El `ResultSet` funciona com un cursor: comença **abans** de la primera fila, i cada `next()`
avança una posició i retorna `true` si hi ha fila. Per això el patró és sempre `while (rs.next())`.

L'accés a les columnes pot ser per posició (`getString(1)`, comença a 1, no a 0) o per nom
(`getString("name")`). Per nom és més llegible i no es trenca si canvia l'ordre de columnes.

### 2.5. Tancar

```java
resultSet.close();
statement.close();
connection.close();
```

En ordre invers a la creació. **Aquest és el punt feble de la classe**: si salta una excepció
abans dels `close()`, els recursos queden oberts. Serveix per introduir la millora que ve
després (try-with-resources).

---

## 3. `dbInsert.java` - crear taula, inserir i llegir

Mateixa estructura que `dbConn`, però ara la URL ja apunta a la base de dades concreta
(`.../house`) i s'hi fan tres operacions.

### 3.1. DDL des de Java

```java
String createTableSQL = "CREATE TABLE IF NOT EXISTS kitchen ("
        + "id INT AUTO_INCREMENT PRIMARY KEY, "
        + "name VARCHAR(50), "
        + "size_sq_meters DECIMAL(5,2), "
        + "has_oven BOOLEAN, "
        + "number_of_cabinets INT, "
        + "floor_material VARCHAR(50))";
statement.executeUpdate(createTableSQL);
```

`IF NOT EXISTS` fa que el programa es pugui tornar a executar sense error.

### 3.2. INSERT i files afectades

```java
int registres = statement.executeUpdate(insertDataSQL1);
registres += statement.executeUpdate(insertDataSQL2);
```

`executeUpdate` retorna quantes files s'han vist afectades. És la manera de comprovar que
l'operació ha fet realment alguna cosa (útil sobretot en `UPDATE` i `DELETE`, on un 0 sovint
vol dir que el `WHERE` no ha trobat res).

### 3.3. Mapatge de tipus SQL a tipus Java

```java
int id = resultSet.getInt("id");
String name = resultSet.getString("name");
double size = resultSet.getDouble("size_sq_meters");
boolean hasOven = resultSet.getBoolean("has_oven");
```

Taula de correspondències que convé que tinguin a mà:

| Tipus MySQL | Mètode del ResultSet | Tipus Java |
|---|---|---|
| `INT` | `getInt` | `int` |
| `VARCHAR` | `getString` | `String` |
| `DECIMAL` / `DOUBLE` | `getDouble` / `getBigDecimal` | `double` / `BigDecimal` |
| `BOOLEAN` (`TINYINT(1)`) | `getBoolean` | `boolean` |
| `DATE` | `getDate` | `java.sql.Date` |
| `DATETIME` | `getTimestamp` | `java.sql.Timestamp` |

Observació: aquest exemple insereix els mateixos registres cada vegada que s'executa, de manera
que la taula es va omplint de duplicats. És intencionat, perquè el següent exemple hi posa una
restricció `UNIQUE` i provoca l'error.

---

## 4. `AlterTableErrors.java` - gestió d'excepcions de veritat

Aquesta és la classe clau del bloc d'excepcions. Hi ha tres idees.

### 4.1. try-with-resources

```java
try (Connection connection = DriverManager.getConnection(url, user, password);
     Statement statement = connection.createStatement()) {
    ...
}
```

Tot el que es declara als parèntesis del `try` es tanca **automàticament** en sortir del bloc,
tant si acaba bé com si salta una excepció. Funciona perquè `Connection`, `Statement` i
`ResultSet` implementen `AutoCloseable`.

Comparació directa amb `dbConn`: 3 línies de `close()` que es poden oblidar, contra 0.
A partir d'aquí, els alumnes haurien de fer-ho sempre així.

### 4.2. Jerarquia d'excepcions: de la més concreta a la més general

```java
try {
    statement.executeUpdate(insertDuplicat);
} catch (SQLIntegrityConstraintViolationException e) {
    System.out.println("Ja existeix una cuina amb aquest nom.");
    System.out.println("Codi MySQL: " + e.getErrorCode()); // 1062
}
```

`SQLIntegrityConstraintViolationException` és **subclasse** de `SQLException`. Regla general:
els `catch` s'avaluen de dalt a baix i s'executa el primer que encaixa, així que la subclasse
sempre ha d'anar abans que la superclasse. Si es posa `catch (SQLException e)` primer, el compilador
avisa que el segon `catch` és inabastable.

Quan salta aquesta excepció: clau duplicada (`UNIQUE` o `PRIMARY KEY`), clau forana inexistent,
`NOT NULL` violat.

Informació útil que porta l'objecte excepció:

- `getMessage()` - descripció textual.
- `getErrorCode()` - codi d'error propi del SGBD (1062 = Duplicate entry a MySQL).
- `getSQLState()` - codi estàndard de 5 caràcters, igual per a tots els SGBD (23000 = violació d'integritat).

### 4.3. Try intern: no tot error ha d'aturar el programa

El `try` de la inserció duplicada és **dins** del `try` gran. Això vol dir que l'error es tracta
localment i el programa continua:

```java
System.out.println("Cuines a la taula: " + comptaCuines(statement));
```

Decisió de disseny a discutir: quan volem que un error avorti l'operació sencera i quan volem
capturar-lo, informar l'usuari i seguir endavant.

### 4.4. `throws` en lloc de `try`

```java
private static int comptaCuines(Statement statement) throws SQLException {
    try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS total FROM kitchen")) {
        rs.next();
        return rs.getInt("total");
    }
}
```

El mètode auxiliar no captura res: declara `throws SQLException` i delega el tractament a qui el
crida. És el patró habitual: els mètodes de baix nivell propaguen, i un sol punt de dalt decideix
què fer amb l'error. Fixeu-vos també en l'àlies `AS total`, que és el que permet fer `getInt("total")`.

Avís que hi ha al capçal del fitxer: aquest exemple no es pot executar dues vegades seguides,
perquè la segona vegada l'`ALTER TABLE` falla. Per repetir-lo cal desfer els canvis a mà:

```sql
ALTER TABLE kitchen DROP INDEX uq_kitchen_name;
ALTER TABLE kitchen DROP COLUMN installed_at, DROP COLUMN last_cleaning;
```

---

## 5. `PreparedStatements.java` - consultes parametritzades

### 5.1. L'esquelet amb interrogants

```java
String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, "
        + "number_of_cabinets, floor_material) VALUES (?,?,?,?,?)";
PreparedStatement prepStatement = connection.prepareStatement(insertKitchen);
```

La consulta es prepara **abans** de saber els valors. Cada `?` és un forat que s'omplirà després.

### 5.2. Omplir els paràmetres

```java
prepStatement.setString(1, sc.nextLine());
prepStatement.setFloat(2, sc.nextFloat());
prepStatement.setBoolean(3, sc.nextBoolean());
prepStatement.setInt(4, sc.nextInt());
sc.nextLine();
prepStatement.setString(5, sc.nextLine());
```

Regles:

- Els índexs comencen a **1**, no a 0. És l'error més freqüent.
- El mètode `setXxx` ha de coincidir amb el tipus de la columna.
- Hi ha d'haver tants `set` com `?`; si en falta un, salta `SQLException` en executar.

El `sc.nextLine()` solt de la línia 5 és el clàssic problema del `Scanner`: `nextInt()` llegeix el
número però deixa el salt de línia al buffer, i cal consumir-lo abans del següent `nextLine()`.

### 5.3. Executar

- `execute()` - genèric, retorna `boolean` (true si hi ha `ResultSet`).
- `executeUpdate()` - per a `INSERT`/`UPDATE`/`DELETE`, retorna files afectades.
- `executeQuery()` - per a `SELECT`, retorna `ResultSet`. **Sense paràmetres**, perquè la
  consulta ja està dins del `PreparedStatement`.

### 5.4. Dos errors a comentar expressament a classe

Aquest fitxer conté dos punts millorables que són molt bon material de debat:

**a) Reutilitzar el PreparedStatement amb un SQL diferent**

```java
ResultSet resultSet = prepStatement.executeQuery(selectSQL); // incorrecte
```

`executeQuery(String)` és un mètode heretat de `Statement`. Cridar-lo sobre un `PreparedStatement`
llança `SQLException` per especificació. El correcte és crear-ne un de nou:

```java
try (PreparedStatement select = connection.prepareStatement("SELECT * FROM kitchen");
     ResultSet rs = select.executeQuery()) {
    ...
}
```

**b) `catch (Exception e)`**

```java
} catch (Exception e) {
    e.printStackTrace();
}
```

Capturar `Exception` ho engloba tot (errors de BD, del `Scanner`, `NullPointerException`...) i
impedeix donar un missatge útil. Millor capturar `SQLException` i, si cal, les altres per separat.
I `printStackTrace()` està bé per depurar, però no és una manera d'informar l'usuari final.

---

## 6. `PreparedStatementsAvancat.java` - per què són obligatoris

Aquesta classe justifica tot el bloc. Cinc apartats.

### 6.1. Injecció SQL

Versió vulnerable, amb concatenació:

```java
String query = "SELECT id, name FROM kitchen WHERE name = '" + nom + "'";
```

Si l'usuari escriu `x' OR '1'='1`, el SQL que s'executa acaba sent:

```sql
SELECT id, name FROM kitchen WHERE name = 'x' OR '1'='1'
```

La condició sempre és certa i la consulta retorna **tota** la taula. Amb un `DELETE` o un `UPDATE`
les conseqüències són pitjors.

Versió segura:

```java
String query = "SELECT id, name FROM kitchen WHERE name = ?";
prepSt.setString(1, nom);
```

La idea central: el driver envia l'esquelet de la consulta i els valors **per separat**. El text de
l'usuari mai s'interpreta com a codi SQL, sempre com a dada. Amb la mateixa entrada maliciosa, la
consulta busca literalment una cuina que es digui `x' OR '1'='1` i no en troba cap.

Executar els dos mètodes seguits i comparar la sortida per pantalla és la millor manera d'ensenyar-ho.

### 6.2. Dates: de `java.time` a `java.sql`

```java
prepSt.setDate(1, Date.valueOf(instalacio));        // LocalDate      -> columna DATE
prepSt.setTimestamp(2, Timestamp.valueOf(neteja));  // LocalDateTime  -> columna DATETIME
```

I en sentit invers, en llegir:

```java
LocalDateTime neteja = rs.getTimestamp("last_cleaning").toLocalDateTime();
```

Punt de confusió habitual: `java.sql.Date` **no** és `java.util.Date`. Cal vigilar els `import`.
La regla pràctica: dins del programa es treballa amb `java.time` (`LocalDate`, `LocalDateTime`), i
només es converteix a `java.sql.*` just en el moment de fer el `set` o el `get`.

### 6.3. LIKE: el comodí va dins del paràmetre

Incorrecte:

```java
"... WHERE name LIKE '%?%'"
```

Dins de cometes simples, el `?` és un caràcter literal, no un paràmetre.

Correcte:

```java
String query = "SELECT id, name FROM kitchen WHERE name LIKE ? ORDER BY name";
prepSt.setString(1, "%" + text + "%");
```

El `%` forma part del **valor**, no de l'esquelet de la consulta.

### 6.4. BETWEEN amb dos paràmetres

```java
"WHERE last_cleaning BETWEEN ? AND ? ORDER BY last_cleaning"
prepSt.setTimestamp(1, Timestamp.valueOf(inici));
prepSt.setTimestamp(2, Timestamp.valueOf(fi));
```

Res especial: simplement es veu que l'ordre dels índexs segueix l'ordre d'aparició dels `?` al text.

### 6.5. WHERE dinàmic

El cas real d'un formulari de cerca amb filtres opcionals. Es construeix el SQL amb `StringBuilder`
i s'acumulen els valors en una llista:

```java
StringBuilder sql = new StringBuilder("SELECT ... FROM kitchen WHERE 1=1");
List<Object> parametres = new ArrayList<>();

if (text != null) {
    sql.append(" AND name LIKE ?");
    parametres.add("%" + text + "%");
}
...
for (int i = 0; i < parametres.size(); i++) {
    prepSt.setObject(i + 1, parametres.get(i));
}
```

Tres coses a destacar:

- **`WHERE 1=1`**: és un truc perquè tots els filtres es puguin afegir amb `AND` sense haver de
  comprovar si són el primer. La condició és sempre certa i no altera el resultat.
- **La llista i el SQL han d'anar sincronitzats**: cada vegada que s'afegeix un `?` al text,
  s'afegeix el valor corresponent a la llista, en el mateix ordre.
- **`setObject`**: deixa que el driver dedueixi el tipus. Còmode quan els paràmetres són heterogenis,
  però amb `setString`/`setInt` es detecten més errors en temps de compilació.

Aquí es concentra tot el que s'ha vist: la consulta es construeix dinàmicament, però els valors de
l'usuari continuen entrant sempre com a paràmetres. Construir l'estructura del SQL és segur;
concatenar-hi dades, no.

---

## 7. Resum: què han de saber fer després

1. Afegir el connector MySQL al `pom.xml` i entendre què fa Maven.
2. Obrir una connexió amb `DriverManager.getConnection` i saber llegir la URL JDBC.
3. Distingir `Connection`, `Statement`, `PreparedStatement` i `ResultSet`.
4. Triar entre `executeQuery` i `executeUpdate` segons el tipus de sentència.
5. Recórrer un `ResultSet` amb `while (rs.next())` i llegir columnes pel seu nom.
6. Tancar recursos sempre amb try-with-resources.
7. Capturar `SQLException` i les seves subclasses en l'ordre correcte, i aprofitar
   `getErrorCode()` i `getSQLState()`.
8. Decidir entre capturar l'excepció o propagar-la amb `throws`.
9. Fer servir `PreparedStatement` amb `?` per a **qualsevol** dada que vingui de fora.
10. Explicar amb un exemple què és una injecció SQL i per què els paràmetres la impedeixen.

## 8. Errors més freqüents de l'alumnat

| Error | Símptoma |
|---|---|
| Índex de paràmetre a 0 | `SQLException: Parameter index out of range` |
| Oblidar un `set` | `SQLException: No value specified for parameter N` |
| `rs.getString(0)` | Les columnes comencen a 1 |
| Llegir sense `rs.next()` | `SQLException: Before start of result set` |
| `executeQuery` en un `INSERT` | `SQLException: Can not issue data manipulation statements with executeQuery()` |
| `'%?%'` al LIKE | No dona error, però no troba mai res |
| Concatenar en lloc de parametritzar | Funciona fins que algú escriu una cometa |
| Connexió no tancada | El servidor acumula connexions fins a `Too many connections` |
