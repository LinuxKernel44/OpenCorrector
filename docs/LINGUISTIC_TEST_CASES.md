# Jeu de tests linguistiques manuels — OpenCorrector

Ce fichier regroupe les cas de test à exécuter manuellement (coller le texte dans une app,
sélectionner, "Corriger avec OpenCorrector") pour valider la qualité de correction en
français et en anglais, sur les trois modes (Corrigé / Plus formel / Plus concis).

Pour chaque cas, vérifier :
- **Langue** : la sortie est dans la même langue que l'entrée.
- **Sens** : aucune dérive sémantique, aucune information inventée ou supprimée.
- **Mode** : le résultat respecte bien le mode demandé (voir prompt/*.txt).
- **Ponctuation** : la ponctuation pertinente est conservée ou corrigée sensiblement.

| # | Langue | Catégorie | Texte d'entrée | Point à vérifier |
|---|--------|-----------|----------------|-------------------|
| 1 | FR | Orthographe | `Je c'est pas si il vien demain ou pas.` | "je sais", "s'il vient" — pas de changement de sens |
| 2 | FR | Grammaire / accords | `Les enfants a mangé toute les pomme du jardin.` | accords sujet/verbe et pluriels corrigés |
| 3 | FR | Conjugaison | `Hier, je vais au marché et j'achète des légumes.` | temps du passé cohérent (je suis allé / j'ai acheté) |
| 4 | FR | Tournure maladroite | `Ce que je veux dire c'est que bon, en fait, on est un peu en retard sur le projet, quoi.` | mode "plus formel" doit rester professionnel sans changer l'intention |
| 5 | FR | Déjà correcte | `Le rapport trimestriel a été transmis à l'ensemble de l'équipe ce matin.` | retour quasi identique, aucune sur-correction |
| 6 | FR | Phrase courte | `Ou est tu ?` | "Où es-tu ?" — ponctuation et accents corrigés |
| 7 | FR | Paragraphe | `Le projet avance bien, on a finis la premiere partie et on commence la deuxieme partie la semaine prochaine, il reste encore beaucoup de travail mais on est confiant pour la suite.` | mode "plus concis" doit réduire sans perdre le planning (partie 1 finie, partie 2 semaine prochaine) |
| 8 | FR | Nombres et noms propres | `M. Dupont a vendu 250 unités à la société Martin & Fils en mars.` | "250", "Dupont", "Martin & Fils" inchangés |
| 9 | EN | Spelling | `I recieve you\'re email but i dont have the fil yet.` | "receive", "your email", "I don\'t have the file" |
| 10 | EN | Grammar / agreement | `She don\'t knows what time the meeting start tomorrow.` | "She doesn\'t know", "the meeting starts" |
| 11 | EN | Conjugation / tense | `Yesterday I go to the store and I buy some milk.` | past tense consistency (went / bought) |
| 12 | EN | Already correct | `The quarterly report was sent to the entire team this morning.` | returned essentially unchanged |
| 13 | EN | Short sentence | `were are you going` | "Where are you going?" |
| 14 | EN | Paragraph / concise mode | `The project is going well, we finished the first part and we are starting the second part next week, there is still a lot of work left but we are confident about what comes next.` | "concise" mode shortens while keeping part 1 done / part 2 next week |
| 15 | EN | Numbers and proper nouns | `Mr. Smith sold 250 units to Johnson & Co in March.` | "250", "Smith", "Johnson & Co" unchanged |
| 16 | FR | Texte long (chunking) | Concaténer ~6 paragraphes similaires au #7 pour dépasser ~900 tokens | l'avertissement de découpage s'affiche, le texte reconstruit garde son sens de bout en bout |

## Procédure

1. Télécharger le modèle "Qualité" (Qwen2.5-1.5B Q4_K_M) depuis l'écran principal.
2. Pour chaque ligne du tableau, sélectionner le texte dans une app tierce (Notes, navigateur, etc.),
   choisir "Corriger avec OpenCorrector", puis tester les 3 modes.
3. Noter tout écart par rapport aux points à vérifier (langue, sens, mode, ponctuation) dans une
   issue GitHub du dépôt avec le numéro du cas de test.
4. Refaire le même passage avec le modèle "Rapide" (Qwen2.5-0.5B) pour comparer la qualité relative.
