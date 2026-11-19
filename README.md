# Peasant King Assistant

Application Android expérimentale permettant de **diagnostiquer les écrans** de Peasant to King 0.1.46 afin de préparer une automatisation des contrats et du réapprovisionnement des ports.

## Statut
- Prototype en **lecture seule** : capture d'écran via service d'accessibilité Android et reconnaissance locale de texte (ML Kit).
- Aucune lecture directe de la sauvegarde du jeu.
- Aucune automatisation des contrats ou transferts activée.
- L'application n'intègre ni APK original ni ressources du jeu.

## Construire l'APK
Ouvrir **Actions → Build Android APK** et, lorsqu'une exécution est réussie, récupérer l'artefact `PeasantKingAssistant-debug-apk`. Il contient un APK *debug* pour essais, et **non** un bot fini.

## Utilisation
Installer l'APK, activer manuellement le service d'accessibilité de l'application, ouvrir Peasant to King, puis toucher le panneau flottant **Analyser**. Le bouton **Copier rapport** copie uniquement les textes OCR et leurs positions approximatives. Vérifier soigneusement les chiffres avant toute action.

## Sécurité
L'autorisation d'accessibilité est sensible. Le code doit être audité avant installation, la permission doit être coupée après les tests et les rapports OCR ne doivent pas être publiés dans ce dépôt public.
