# MiniJeux (Paper 1.21)

Lobby + Duels + Spleef dans un seul plugin.

## Compiler
Prérequis : Java 21 et Maven.

    mvn clean package

Le plugin est généré dans `target/MiniJeux.jar` : copiez-le dans le dossier `plugins/` du serveur Paper, puis redémarrez.

## Configuration rapide (en tant qu'opérateur)

### Lobby
1. Placez-vous au spawn voulu : `/setlobby`

### Duels
1. Pour chaque arène, placez-vous au point de départ du joueur 1 (en regardant vers le centre) : `/duelarena spawn1 arene1`
2. Puis au point du joueur 2 : `/duelarena spawn2 arene1`
3. `/duelarena list` pour vérifier, `/duelarena delete <nom>` pour supprimer.

### Spleef
1. Construisez un sol (idéalement en blocs de neige `SNOW_BLOCK`), sur une ou plusieurs couches.
2. Regardez un coin du sol : `/spleef pos1`, puis le coin opposé : `/spleef pos2`.
   (Les deux blocs doivent être dans le même monde ; la zone est le cuboïde entre eux.)

## Commandes joueurs
- `/jeux` ou boussole : menu des jeux
- `/duel <joueur>` / `/duel accept` / `/duel deny` : défi direct
- `/duel queue` / `/duel leave` : file d'attente automatique
- `/duel stats` : victoires/défaites
- `/spleef join` / `/spleef leave`
- `/lobby`

## Permission
- `minijeux.admin` (op par défaut) : setlobby, duelarena, spleef pos1/pos2, build en créatif dans le lobby.

## Personnalisation
- `config.yml` : durée du compte à rebours, joueurs min/max du Spleef...
- Kit de duel : méthode `giveKit` dans `DuelManager.java`.

## Aternos

Le plugin fonctionne sur **Paper 1.20.x et 1.21.x** (Spigot/Bukkit pur ne suffit pas : il faut Paper).

1. Sur Aternos, onglet **Logiciel** : choisissez **Paper** et une version 1.20 ou 1.21 (Aternos règle Java tout seul).
2. Obtenez `MiniJeux.jar` (voir ci-dessous), puis onglet **Fichiers** > dossier `plugins` > **Upload** (ou onglet **Plugins** > Upload si disponible).
3. Démarrez le serveur. Dans la **Console** Aternos, tapez : `op VotrePseudo`.
4. Faites la configuration (`/setlobby`, `/duelarena ...`, `/spleef pos1/pos2`). Tout est sauvegardé dans les fichiers du plugin, donc conservé quand Aternos s'éteint.

### Obtenir le .jar sans rien installer
1. Créez un compte gratuit sur github.com et un nouveau dépôt.
2. Envoyez-y tout le contenu de ce dossier (bouton « Add file > Upload files », y compris le dossier `.github`).
3. Onglet **Actions** : le build se lance tout seul (1 à 2 minutes). Ouvrez-le et téléchargez l'artefact **MiniJeux** : il contient `MiniJeux.jar`.

Astuce Aternos : pensez à désactiver le mode « whitelist » si vous voulez des joueurs libres, et gardez le serveur en `online-mode` selon votre besoin (les joueurs crack nécessitent de le couper, à vos risques).
