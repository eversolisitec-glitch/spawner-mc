# Spawners (Paper 26.1.2)

Compilar: sube esta carpeta a GitHub; la pestaña Actions compila el .jar (Artifacts > Spawners). Necesita Java 25.

## Tienda
Para vender spawners, que la tienda ejecute (como consola):
  spawner dar {player} zombie 1
Para vender estrellas (se suman al saldo digital del jugador):
  spawner estrellas {player} 10

## Config
Todo (costos, niveles, probabilidades) está en plugins/Spawners/config.yml. Recarga con /spawner recargar.

## Estrellas digitales
No son ítems: se guardan en el jugador y se ven en pantalla (modo en config.yml: ACTIONBAR, BOSSBAR o NINGUNO).
/spawner saldo  - ver cuántas tienes
/spawner pantalla  - mostrar/ocultar el contador

## PlaceholderAPI
Si PlaceholderAPI esta instalado, se registra %spawners_estrellas% (saldo de estrellas del jugador).
Para mostrarlo en TAB u otros plugins usa pantalla.modo: NINGUNO para no duplicarlo en pantalla.
