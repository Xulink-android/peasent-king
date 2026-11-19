# Peasant King Assistant — v1.5 experimental

Application Android indépendante pour Peasant to King 0.1.46. Le dépôt est **public** : aucun APK du jeu, sauvegarde ou image propriétaire n'est inclus.

## Fonctionnalités
- Capture d'écran manuelle via service d'accessibilité Android et OCR ML Kit local.
- Panneau flottant avec **Analyser**, **Copier rapport**, **Suivi ON/OFF**, **Réduire**, **X Fermer** et déplacement par glissement sur le fond.
- Suivi OCR facultatif toutes les 5 secondes (uniquement quand le jeu est au premier plan).
- Interprétation **indicative** des quantités « ENVOYER N » et du contexte de l'écran.
- Planificateur Kotlin de transferts régions → port, avec contrôle de capacité, arrivages, réserve régionale et chargement prévu.
- Tests unitaires du parseur et du planificateur exécutés par GitHub Actions avant compilation.

## Limitations essentielles
**Aucun chargement, transfert, clic ou envoi automatique n'est activé.**
Les contrats affichent des **icônes** de marchandises : l'OCR seul ne permet pas d'en déduire la ressource. L'application refuse de deviner une ressource ou un stock. Le planificateur n'est pas encore alimenté par les valeurs réelles du jeu. L'analyse des contrats est une simulation/diagnostic et non un bot complet.

## APK
Dans **Actions → Build Android APK → dernier build vert → Artifacts**, télécharger `PeasantKingAssistant-debug-apk`, décompresser puis installer `app-debug.apk`.

## Sécurité
Le service d'accessibilité est une permission sensible. L'application n'envoie pas de gestes et ne transmet pas de rapport sur un serveur. Le bouton **X Fermer** appelle `disableSelf()` pour couper le service. En cas de problème : Paramètres Android → Accessibilité → Applications installées → Peasant King Assistant → Désactiver.

## Étapes restantes pour un bot autonome
1. Identifier de manière vérifiable les icônes des marchandises et les écrans de stocks/entrepôts.
2. Associer les stocks réels et les arrivages à des ressources et des ports.
3. Mettre en place un moteur d'actions avec vérification après chaque transition et arrêt immédiat.
4. Tester sur appareil réel, puis activer l'automatisation uniquement lorsque la reconnaissance est fiable.
