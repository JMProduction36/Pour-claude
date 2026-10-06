# PocketDoor 0.10.5

- Corrige l’orientation du rendu quand la Porte de Poche est placée à droite ou à gauche du joueur.
- Conserve le retournement de rendu de 180° validé précédemment.
- Inverse uniquement le sens de compensation pour les orientations latérales EAST/WEST afin d’éviter le rendu inversé.

## 0.10.6
- Carte de téléportation réellement exploitable : sélection précise à la souris sur les zones découvertes.
- Carte recentrée au milieu de l'écran au lieu d'être ancrée en bas.
- Affichage des coordonnées X/Z exactes sous l'indication du zoom lorsque la souris est au-dessus de la carte.
- Indication visuelle « Zone découverte / Zone inexplorée » sous les coordonnées.
- Le zoom reste disponible à la molette et s'applique directement à l'étendue du monde affichée.


## 0.10.7
- Corrige la découverte de la carte : les premiers ticks pouvaient empêcher complètement la capture à cause de l'initialisation avec `Long.MIN_VALUE`.
- La première zone autour du joueur est maintenant enregistrée immédiatement, puis l'exploration continue normalement en marchant.
- La sauvegarde du cache utilise le même correctif d'initialisation.

## 0.10.8
- Téléportation réelle depuis la carte : la confirmation envoie désormais le joueur à la position X/Z choisie dans l'Overworld.
- Le serveur calcule la hauteur de surface et enregistre la destination.

## 0.10.9
- La confirmation sur la carte ne téléporte plus le joueur.
- La Porte de Poche extérieure est réellement déplacée aux coordonnées X/Z sélectionnées.
- Le joueur reste dans le bureau pendant le déplacement, donnant l'effet visuel que la porte s'est téléportée.
- Le rendu Immersive Portals suit automatiquement la nouvelle position lorsque la porte est rouverte.


## 0.10.10
- La destination est maintenant préparée immédiatement après la confirmation sur la carte.
- Le chunk de destination est chargé côté serveur sans déplacer le joueur.
- Les deux portails Immersive Portals sont recréés immédiatement aux nouvelles coordonnées.
- Il n'est plus nécessaire de fermer/réouvrir la porte pour que le render connaisse le nouvel emplacement.
- La porte physique reste fermée pendant le chargement ; le joueur reste dans le bureau.
