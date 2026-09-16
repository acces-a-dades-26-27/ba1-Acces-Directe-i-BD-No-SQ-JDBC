# BA1 - JDBC: execució per lots (batch)

Aquí treballem el pas d'enviar les sentències d'una en una a enviar-les en bloc, i les conseqüències que això té sobre el rendiment i sobre la gestió d'errors.

Ordre de lectura recomanat:

1. `BatchSimple.java` (el mecanisme bàsic: addBatch + executeBatch)
2. `BatchAvancat.java` (blocs, batch amb `Statement`, i què passa quan una fila falla)

Requisits previs: la base de dades `house` i la taula `kitchen` ja creades (`dbInsert`), i la
restricció `UNIQUE` sobre `name` afegida (`AlterTableErrors`). 

El tercer exemple de `BatchAvancat` depèn d'aquesta restricció; sense ella, no falla res i l'exemple perd la gràcia.

---

## 0. El problema que resol el batch

Amb el que s'ha vist fins ara, inserir 120 files vol dir 120 crides a `executeUpdate()`, és a dir,
120 anades i tornades al servidor. El cost no és el `INSERT` en si, que és rapidíssim, sinó el
**viatge per la xarxa** de cada sentència.

El batch acumula sentències a la banda del client i les envia totes juntes en una sola crida:

| Enfocament | Viatges al servidor per a 120 files |
|---|---|
| `executeUpdate()` dins d'un bucle | 120 |
| `addBatch()` + un `executeBatch()` | 1 |
| `addBatch()` + blocs de 50 | 3 |

Idea a fixar: el batch no fa que el SQL s'executi més de pressa, fa que es **transmeti** millor.

---

## 1. `BatchSimple.java` - el mecanisme bàsic

### 1.1. Mateix PreparedStatement, moltes files

```java
String insertKitchen = "INSERT INTO kitchen (name, size_sq_meters, has_oven, "
        + "number_of_cabinets, floor_material) VALUES (?,?,?,?,?)";
PreparedStatement prepStatement = connection.prepareStatement(insertKitchen);
```

L'esquelet és el de sempre. La novetat és que es prepara **una sola vegada** i es reutilitza per a
totes les files. Aquest és el motiu pel qual el batch i els `PreparedStatement` van tan de la mà:
comparteixen la mateixa consulta i només canvien els valors.

### 1.2. Les dades: un array bidimensional

```java
Object[][] cuines = {
        {"b1", 12.22, false, 2, "Linoli"},
        {"b2", 2.52, true, 4, "Parquet"},
        {"b3", 14.02, false, 3, "Alumini"},
        {"b4", 11.99, true, 9, "Pedra"},
};
```

Cada fila de l'array és una fila de la taula. Serveix per simular unes dades que, en un cas real,
vindrien d'un fitxer CSV, d'un formulari o d'un altre sistema.

Cal comentar el cost d'aquest disseny: com que és `Object[][]`, tots els valors s'han de convertir
amb un cast en llegir-los (`(String) cuina[0]`, `(Double) cuina[1]`...). Si algun valor no és del
tipus esperat, el programa peta en temps d'execució amb `ClassCastException`, no en compilar.
En un projecte real es faria amb una classe `Cuina` i una `List<Cuina>`, i el compilador ja vigilaria.

### 1.3. El bucle: `set` + `addBatch`

```java
for (Object[] cuina : cuines) {
    prepStatement.setString(1, (String) cuina[0]);
    prepStatement.setDouble(2, (Double) cuina[1]);
    prepStatement.setBoolean(3, (boolean) cuina[2]);
    prepStatement.setInt(4, (Integer) cuina[3]);
    prepStatement.setString(5, (String) cuina[4]);

    prepStatement.addBatch();
}
```

El patró és sempre el mateix i és el que heu de memoritzar:

1. Omplir **tots** els paràmetres amb els `setXxx`.
2. Cridar `addBatch()`, que fa una foto dels valors actuals i l'afegeix a la cua.
3. Tornar a començar amb la fila següent.

`addBatch()` **no** executa res ni parla amb el servidor: només guarda els valors en una llista
interna del `PreparedStatement`. Si s'oblida el `addBatch()` dins del bucle, cada iteració
sobreescriu els paràmetres de l'anterior i al final només queda l'última fila.

### 1.4. `executeBatch()` i el que retorna

```java
long tempsInici = System.currentTimeMillis();
int[] resultats = prepStatement.executeBatch();
long tempsFinal = System.currentTimeMillis();
```

`executeBatch()` envia tota la cua de cop i buida el batch. Retorna un `int[]` amb **una posició
per sentència**, en el mateix ordre en què es van afegir:

| Valor | Significat |
|---|---|
| `>= 0` | nombre de files afectades per aquella sentència |
| `Statement.SUCCESS_NO_INFO` (-2) | s'ha executat bé, però el driver no sap quantes files |
| `Statement.EXECUTE_FAILED` (-3) | aquella sentència ha fallat |

Per això `resultats.length` diu quantes sentències s'han processat i la suma diu quantes files
s'han tocat. La mesura de temps amb `System.currentTimeMillis()` està pensada per comparar-la amb
un bucle d'`executeUpdate()`: val la pena fer l'experiment amb unes quantes centenars de files per veure'n la diferència.

Detall que sorprèn: a MySQL, si s'activa `rewriteBatchedStatements=true` a la URL de connexió, el
driver ajunta els `INSERT` en una sola sentència gegant (molt més ràpid) però llavors retorna
`SUCCESS_NO_INFO` (-2) a cada posició, i la suma de l'array surt negativa. És un bon exemple de
per què no s'ha de confiar cegament en aquest sumatori.

---

## 2. `BatchAvancat.java` - tres situacions reals

### 2.1. Inserció en blocs

```java
private static final int MIDA_BLOC = 50;
```

El problema d'acumular-ho tot en un sol batch: les files pendents viuen a la memòria del programa
Java. Amb 120 files no passa res; amb 500.000 el programa es queda sense memòria. La solució és
partir la feina en blocs.

```java
prepSt.addBatch();
pendents++;

if (pendents == MIDA_BLOC) {
    int[] resultats = prepSt.executeBatch();
    inserides += Arrays.stream(resultats).sum();
    pendents = 0;
}
```

Cada vegada que el comptador arriba a 50, s'executa el bloc i es reinicia. `executeBatch()` buida
la cua, per tant l'objecte es pot continuar reutilitzant.

**El bloc residual** és la part que tothom oblida:

```java
if (pendents > 0) {
    int[] resultats = prepSt.executeBatch();
    ...
}
```

Amb 120 files i blocs de 50, el bucle executa dos blocs complets i en queden 20 al tinter. Sense
aquest `if` final, aquestes 20 files no s'inseririen mai i **no hi hauria cap error**: simplement
faltarien dades. És l'error més difícil de detectar de tot el tema, perquè el programa acaba
dient "tot correcte".

Val la pena calcular la sortida esperada abans d'executar: `50 + 50 + 20 = 120`.

Sobre la generació de dades:

```java
prepSt.setString(1, "Cuina generada " + System.currentTimeMillis() + "-" + i);
prepSt.setDouble(2, Math.round((5 + rnd.nextDouble() * 20) * 100.0) / 100.0);
```

- El `currentTimeMillis()` al nom és el truc per garantir noms únics i no xocar amb el `UNIQUE`
  en re-executar el programa.
- El `Math.round(x * 100.0) / 100.0` arrodoneix a dos decimals, que és el que admet la columna
  `DECIMAL(5,2)`.

### 2.2. Batch amb `Statement`: sentències diferents

```java
try (Statement statement = connection.createStatement()) {
    statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters + 0.5 WHERE floor_material = 'Linoli'");
    statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters - 0.5 WHERE floor_material = 'Parquet'");
    statement.addBatch("UPDATE kitchen SET size_sq_meters = size_sq_meters * 1.10 WHERE floor_material = 'Pedra'");

    int[] resultats = statement.executeBatch();
}
```

Aquí hi ha la diferència conceptual important del document:

| | `PreparedStatement.addBatch()` | `Statement.addBatch(String)` |
|---|---|---|
| SQL | un de sol, amb `?` | un de complet i diferent per crida |
| Què s'acumula | jocs de paràmetres | sentències senceres |
| Quan fer-lo servir | moltes files, mateixa operació | poques operacions, diferents entre elles |
| Injecció SQL | protegit | vulnerable si es concatena |

La lectura del resultat segueix la mateixa regla: una posició per sentència, en ordre d'inserció.

```java
String[] etiquetes = { "Linoli", "Parquet", "Pedra" };
for (int i = 0; i < resultats.length; i++) {
    System.out.println("  " + etiquetes[i] + ": " + resultats[i] + " cuines modificades");
}
```

> Observació: aquest array d'etiquetes paral·lel funciona però és fràgil. Si algú afegeix un quart `addBatch()` i no toca l'array, salta `ArrayIndexOutOfBoundsException`. És el mateix problema de sincronització manual que ja va sortir amb el `WHERE` dinàmic.

### 2.3. Quan una fila falla: `BatchUpdateException`

L'exemple envia tres insercions on la segona té un nom que ja existeix (`Cuina Principal`).

```java
} catch (BatchUpdateException e) {
    int[] comptadors = e.getUpdateCounts();
    for (int i = 0; i < comptadors.length; i++) {
        String estat = comptadors[i] == Statement.EXECUTE_FAILED ? "ERROR" : comptadors[i] + " fila/es";
        System.out.println("  Fila " + (i + 1) + " (" + noms[i] + "): " + estat);
    }
}
```

Tres conclusions:

**a) `BatchUpdateException` és subclasse de `SQLException`.** Per tant va abans al `catch`, igual
que passava amb `SQLIntegrityConstraintViolationException`. Aquí es veu el mateix principi aplicat
a un cas diferent.

**b) `getUpdateCounts()` diu què ha passat fila per fila.** És l'únic lloc d'on es pot treure aquesta
informació: un `catch (SQLException e)` genèric la perdria. Aquest és l'argument pràctic de per què
val la pena capturar la subclasse concreta.

**c) El batch NO és atòmic.** Amb autocommit actiu (el valor per defecte), les files que s'han
executat correctament abans de l'error **ja estan gravades a la base de dades**. El batch no és
"tot o res".

Segons el driver i la configuració, en trobar un error pot aturar-se allà mateix o continuar amb
la resta de sentències i marcar-les amb `EXECUTE_FAILED`. Això vol dir que no es pot assumir el
comportament: s'ha de mirar sempre l'array.

### 2.4. El que falta: transaccions

És la conclusió natural de l'apartat anterior i el pont cap al tema següent. Si el que volem és
"tot o res", cal desactivar l'autocommit:

```java
connection.setAutoCommit(false);
try {
    // ... addBatch() ...
    prepSt.executeBatch();
    connection.commit();       // confirmem tots els canvis de cop
} catch (SQLException e) {
    connection.rollback();     // desfem-ho tot
} finally {
    connection.setAutoCommit(true);
}
```

Amb això, o s'insereixen les tres files o no se n'insereix cap. Això ho veurem amb detall a la BA següent.

---

## 3. Errors més freqüents

| Error | Símptoma |
|---|---|
| Oblidar `addBatch()` dins del bucle | Només s'insereix l'última fila, sense cap avís |
| Oblidar el bloc residual | Falten les últimes N files i el programa diu que tot ha anat bé |
| No reiniciar el comptador de pendents | El bloc no es torna a disparar mai |
| Cridar `executeUpdate()` en comptes d'`executeBatch()` | `SQLException: Can not issue executeUpdate() when the batch is not empty` |
| Esperar que el batch sigui atòmic | Amb autocommit, les files anteriors a l'error queden gravades |
| Capturar només `SQLException` | Es perd `getUpdateCounts()` i no se sap quina fila ha fallat |
| Sumar l'`int[]` sense mirar-ne els valors | Amb `SUCCESS_NO_INFO` (-2) el total surt negatiu |
| Batch de 500.000 files sense blocs | `OutOfMemoryError` o consum de memòria disparat |
