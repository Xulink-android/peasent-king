# Peasant King Assistant — bêta installable (navigation Port uniquement)

**Statut actuel :** l'APK de diagnostic peut être installée, mais l'envoi de marchandises et la gestion autonome des contrats ne fonctionnent pas encore. Le bouton « Auto Port (1 fois) » peut demander **un unique geste de navigation vers Port** lorsque le jeu est au premier plan, uniquement après une activation volontaire, trois observations OCR stables et le contrôle des coordonnées. Ce geste n'est pas validé sur un téléphone réel ; il n'implique pas qu'un chargement ou une livraison ont eu lieu. L'application ne nécessite pas d'application tierce de scripting, ni de root.

**Téléchargement de la bêta :** ouvrir [Actions — Build Android APK](https://github.com/Xulink-android/peasent-king/actions/workflows/build-apk.yml), sélectionner le dernier run réussi, télécharger l'artefact `PeasantKingAssistant-debug-apk`, extraire `app-debug.apk`, puis installer normalement dans Android si le système le permet. L'activation du service d'accessibilité nécessite une décision explicite de l'utilisateur dans les paramètres de Samsung/Android. **Ne jamais contourner** les protections contre les « paramètres restreints ». L'APK reste signée avec une clé debug éphémère : une mise à jour future peut échouer et exiger une désinstallation, avec perte possible de réglages et nouvelles autorisations. Attendre une signature de diffusion stable pour une utilisation durable.

**Fonctionnement :** l'OCR, les références d'icônes extraites de l'APK et les propositions d'expédition sont encore expérimentaux. Les propositions sont uniquement informatives et ne commandent **aucun envoi**. Le service s'arrête d'agir hors de Peasant to King, et le bouton « X Masquer » ne désactive pas l'accès au système : le retirer dans Paramètres → Accessibilité.

---

# Peasant King Assistant — v2.17 bêta

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

## v2.1 — reconnaissance locale expérimentale

- Les découpes candidates peuvent être étiquetées volontairement (charbon, blé, pommes de terre, bois, cacao, soie) dans la galerie.
- Les empreintes visuelles sont enregistrées dans les préférences privées de l'application, sans réseau. Un bouton permet de supprimer les étiquettes.
- Les nouveaux contrats visibles sont comparés aux exemples locaux avec un seuil strict et une marge d'ambiguïté. Les correspondances restent **diagnostiques** : elles ne sont pas encore injectées comme ressource certaine dans le planificateur, et n'autorisent aucun clic.
- La position des découpes reste approximative. Ne pas attribuer une étiquette à une découpe montrant du texte, un fond ou plusieurs objets.

## v2.14 — phases navales observées dans la vidéo

- La classification OCR distingue maintenant l'écran de chargement (`CHARGEMENT DE MARCHANDISES`), la cargaison prête (`CARGAISON PRÊTE`) et la récupération de paiement (`DÉCHARGEMENT AU PORT` avec `Récupérer`). Ces libellés et leur ordre ont été relevés dans la vidéo du jeu fournie.
- Cette reconnaissance ajoute des phases de diagnostic. Elle n'envoie toujours aucun geste de chargement, de départ, de déchargement ou de collecte. L'exécution réelle et sa vérification sur le Galaxy restent à développer et à valider avant toute utilisation automatique.

## v2.15 — socle de gestes à cibles observées

- Un résolveur de cibles utilise les rectangles OCR du jeu pour viser le bouton de destination et le paiement, et pour proposer un glissement depuis une ligne de stock reconnue vers la zone de chargement observée. Les propositions sont rejetées si la ressource, la quantité, la destination, le stock ou la capacité ne concordent pas.
- Un adaptateur Android commun vérifie l'accord utilisateur, le premier plan, la fraîcheur de la capture et les deux extrémités d'un glissement. La navigation Port existante passe maintenant par cet adaptateur.
- Le résolveur de chargement/destination/paiement est testé en isolation mais n'est pas encore orchestré par `ShipmentExecutionMachine`. Aucune expédition complète n'est activée dans cette version ; il faut encore raccorder le cycle d'états, la lecture réelle des stocks par icônes, l'arrêt Démarrer/Arrêter et valider le tout sur le Galaxy.

## v2.16 — machine de cycle complet

- Une machine déterministe couvre désormais le contrat, le chargement, l'envoi, la vérification du départ, l'arrivée, le déchargement et la collecte du paiement. Elle bloque les stocks insuffisants, les observations ambiguës, les gestes échoués et ne retente jamais un départ ou une collecte non confirmés.
- La machine et le résolveur de gestes sont encore des composants testés séparément : ils ne sont pas encore orchestrés ensemble par le service d'accessibilité. L'APK ne réalise donc pas encore le cycle automatiquement.

## v2.17 — liaison machine/cibles et preuve de récompense

- Le contrat OCR transmet aussi la récompense au plan. La machine refuse de démarrer sans récompense connue et positive.
- Un pont relie les décisions de cycle aux cibles OCR correspondantes et arrête le cycle si la cible visible est absente ou ambiguë. Il couvre l'ouverture du Port, le chargement, le départ, le déchargement et la collecte.
- Le pont est validé par tests unitaires mais reste à brancher aux observations en direct du service. L'extraction automatique des stocks/réservations de la grille d'icônes est encore incomplète ; l'automatisation complète n'est donc pas activée.

## Audit de la grille observée

- La vidéo montre un entrepôt de 4 colonnes × 3 rangées. Les quantités sont affichées comme `111/200` sous les piles ; `200` est la capacité d'une pile, pas une réserve. Il ne faut donc pas les faire interpréter par `StockScreenParser` comme des lignes de stock nommées.
- Le panneau filmé contient des piles de blé, pommes de terre, charbon, bois et une icône d'armes. Le classificateur embarqué ne connaît que six autres icônes extraites comme références d'illustration ; les empreintes d'illustration ne reconnaissent pas de manière fiable ces piles rendues dans l'entrepôt. Les armes ne sont pas modélisées dans le planificateur.
- Les valeurs visibles ne prouvent pas non plus les réservations de cargaisons d'autres navires. `VerifiedStockGate` rejette donc cette grille et le cycle automatique reste désactivé. Ce blocage est volontaire : aucune cargaison ne doit être chargée à partir d'une simple quantité OCR sans ressource confirmée.
- La vidéo confirme visuellement une expédition manuelle (chargement, départ, arrivée, déchargement, paiement), mais elle ne valide pas l'exécution de ces gestes par l'application. Il faut encore calibrer la reconnaissance des piles sur l'écran réel, raccorder le service et le bouton Démarrer/Arrêter, puis vérifier le cycle sur le Galaxy S24 Ultra.
- Le workflow actuel exécute les tests unitaires et `assembleDebug`. L'APK est installable mais signée debug ; une signature stable pour les mises à jour n'est pas encore configurée. Aucun secret ni clé privée n'est présent dans le dépôt.
