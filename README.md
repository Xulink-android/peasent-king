# Peasant King Assistant — v1.8 experimental

Application Android indépendante pour Peasant to King 0.1.46. Le dépôt est **public** : aucun APK du jeu, sauvegarde ou image propriétaire n'est inclus.

## Fonctionnalités
- Capture d'écran manuelle via service d'accessibilité Android et OCR ML Kit local.
- Panneau flottant avec **Analyser**, **Copier rapport**, **Suivi ON/OFF**, **Réduire**, **X Fermer** et déplacement par glissement sur le fond.
- Suivi OCR facultatif toutes les 5 secondes (uniquement quand le jeu est au premier plan).
- Interprétation **indicative** des quantités « ENVOYER N » et du contexte de l'écran.
- Calculateur de transferts régions → port accessible depuis l'écran principal, avec saisie manuelle, contrôle de capacité, arrivages et réserve régionale. Les cargaisons réservées restent comptées dans le stock jusqu'au départ réel.
- Planificateur d'expéditions : contrats, réservations, capacité des navires, stocks partagés et absence de double allocation. Saisie manuelle dans l'application.
- Contrôle de stabilité OCR sur trois observations identiques avant de considérer un contrat complet comme fiable.
- Capture manuelle de découpes d'icônes candidates depuis les contrats et galerie privée sur le téléphone. Ces découpes sont approximatives, non étiquetées et ne déclenchent jamais d'action.
- Tests unitaires du parseur et des planificateurs exécutés par GitHub Actions avant compilation.

## Limitations essentielles
**Aucun chargement, transfert, clic ou envoi automatique n'est activé.**
Les contrats affichent des **icônes** de marchandises : l'OCR seul ne permet pas d'en déduire la ressource. L'application refuse de deviner une ressource ou un stock. Le planificateur n'est pas encore alimenté automatiquement par les valeurs réelles du jeu : il faut les saisir dans l'application. L'analyse des contrats est une simulation/diagnostic et non un bot complet.

## APK
Dans **Actions → Build Android APK → dernier build vert → Artifacts**, télécharger `PeasantKingAssistant-debug-apk`, décompresser puis installer `app-debug.apk`.

## Sécurité
Le service d'accessibilité est une permission sensible. L'application n'envoie pas de gestes et ne transmet pas de rapport sur un serveur. Le bouton **X Fermer** appelle `disableSelf()` pour couper le service. En cas de problème : Paramètres Android → Accessibilité → Applications installées → Peasant King Assistant → Désactiver.

## Étapes restantes pour un bot autonome
1. Identifier de manière vérifiable les icônes des marchandises et les écrans de stocks/entrepôts.
2. Associer les stocks réels et les arrivages à des ressources et des ports.
3. Mettre en place un moteur d'actions avec vérification après chaque transition et arrêt immédiat.
4. Tester sur appareil réel, puis activer l'automatisation uniquement lorsque la reconnaissance est fiable.


## Sécurité et autorisations (v1.9)

- L'application n'utilise pas INTERNET, stockage externe, SMS, contacts ou accès administrateur.
- La capture d'écran passe par l'API Accessibility Android, activée explicitement dans les paramètres. Android/Samsung peuvent imposer une confirmation système : aucun contournement n'est prévu.
- Le bouton **X Masquer** ferme seulement l'interface flottante. Le service reste activé et peut afficher le panneau à nouveau au prochain retour dans le jeu. Pour retirer l'accès, désactiver le service dans les paramètres d'accessibilité Android.
- Le rapport OCR et les découpes d'icônes restent dans le stockage privé de l'application. Copier un rapport dans le presse-papiers est une action volontaire de l'utilisateur.
- Les APK de debug produits par GitHub Actions peuvent être signés avec des clés différentes d'une compilation à l'autre. **Ne pas désinstaller l'application en supposant qu'une mise à jour sera possible** : la distribution de mises à jour fiables nécessitera une clé de signature persistante stockée dans des secrets GitHub Actions, jamais dans le dépôt public.
- Aucun geste automatique n'est envoyé au jeu dans cette version.
