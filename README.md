# PocketDoor 0.10.0

Minecraft 1.19.2 / Fabric.

Cette version ajoute une première vraie carte de destination dans le Pocket Office.
La carte garde en mémoire les zones explorées et affiche le reste en noir, puis permet de sélectionner une destination découverte.

Interagir avec la table de cartographie située dans le bureau pour ouvrir la carte.


### 0.10.9 — déplacement réel de la Porte
La carte de téléportation déplace désormais physiquement la Porte de Poche dans l’Overworld aux coordonnées sélectionnées, sans déplacer le joueur. Le joueur reste dans le bureau et peut rouvrir la porte pour voir le nouvel emplacement via Immersive Portals.

## 0.10.3
Le rendu des portails utilise une rotation fixe de 180° qui reste identique après suppression et recréation de la Porte de Poche.


### 0.10.4
- Restauration de la compensation de rotation du render par rapport à l’orientation de la porte dans l’Overworld.
- Le bureau de destination conserve ainsi la même orientation visuelle fixe, quelle que soit la direction de la porte extérieure.


### 0.10.8 — découverte corrigée
La carte enregistre bien la zone parcourue dès la première capture, puis étend progressivement les zones découvertes lorsque le joueur explore l'Overworld.


## 0.10.8
- La carte de téléportation déclenche maintenant une vraie téléportation côté serveur après validation.
- La position Y est calculée automatiquement avec la hauteur de surface de l'Overworld.
- La dernière destination choisie reste enregistrée dans les données du monde.


### 0.10.10 — chargement immédiat de la destination
Après la validation d'une destination sur la carte, le chunk cible et les portails Immersive Portals sont préparés immédiatement. Le joueur reste dans le bureau et la porte extérieure est déplacée sans téléportation du joueur. L'ouverture de la porte utilise directement le nouvel emplacement.
