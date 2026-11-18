# Y4 Battery

Appli Android (8.0+) qui limite les applis en arrière-plan pour économiser la batterie.

## Fonctions
- **Nettoyer maintenant** : tue les processus en arrière-plan de toutes tes applis et affiche la RAM libérée.
- **Liste à geler** : un interrupteur par appli. Avec Shizuku, l'appli est interdite d'arrière-plan
  (`RUN_ANY_IN_BACKGROUND ignore` + bucket `restricted`) et arrêtée de force à chaque nettoyage.
- **Nettoyage auto** toutes les 15 min.
- Dernière utilisation de chaque appli, et ❄️ pour celles qui sont gelées.
- Les applis vitales (services Google, Play Store, launcher, clavier, téléphone, Shizuku) sont protégées.
- Toucher une appli ouvre sa page dans les réglages (pour mettre « Batterie : Restreinte » à la main).

## Obtenir l'APK sans Android Studio
1. Crée un dépôt sur GitHub et pousse ce dossier.
2. Onglet **Actions** → le workflow « Build APK » se lance tout seul (~4 min).
3. Ouvre l'exécution terminée → télécharge **Y4Battery-apk** (zip contenant `app-debug.apk`).
4. Installe-le sur ton téléphone (autorise les sources inconnues).

Avec Android Studio : ouvrir le dossier, puis *Build > Build APK(s)*.

## Mode avancé (Shizuku, sans root)
1. Installe Shizuku (Play Store ou https://shizuku.rikka.app).
2. Démarre-le par débogage sans fil (Options développeur).
3. Dans Y4 Battery, touche « Autoriser » sur la ligne Shizuku.

À refaire après chaque redémarrage du téléphone (limite de Shizuku sans root).

## Limites
- Sans Shizuku, Android ne laisse tuer que les processus déjà en arrière-plan ; certaines applis redémarrent seules.
- Une appli gelée ne reçoit plus de notifications ni d'alarmes tant que tu ne l'ouvres pas : ne gèle pas
  WhatsApp/ton réveil si tu en as besoin.
- Nettoyer en boucle des applis que tu utilises souvent peut coûter plus de batterie (elles redémarrent à froid).
  Le gain vient surtout de la liste à geler.
