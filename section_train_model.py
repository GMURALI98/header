
import spacy
import pickle
nlp=spacy.load('en_core_web_sm')

# Getting the pipeline component
ner=nlp.get_pipe("ner")

with open ('Train_data_section', 'rb') as fp:
    TRAIN_DATA = pickle.load(fp)



LABEL1 = 'Act'
# LABEL2 = 'Section'
ner.add_label(LABEL1)
# ner.add_label(LABEL2)
pipe_exceptions = ["ner", "trf_wordpiecer", "trf_tok2vec"]
unaffected_pipes = [pipe for pipe in nlp.pipe_names if pipe not in pipe_exceptions]

import random
from spacy.util import minibatch, compounding
from spacy.training import Example
from pathlib import Path

# TRAINING THE MODEL
with nlp.disable_pipes(*unaffected_pipes):

  # Training for 30 iterations
  for iteration in range(30):

    # shuufling examples  before every iteration
    random.shuffle(TRAIN_DATA)
    losses = {}
    # batch up the examples using spaCy's minibatch
    sizes=compounding(1.0, 4.0, 1.001)
    # batches = minibatch(TRAIN_DATA, size=compounding(4.0, 32.0, 1.001))
    batches = minibatch(TRAIN_DATA, size=sizes)   
    j=1
    for batch in batches:
            texts, annotations = zip(*batch)
            
            example = []
            losses = {}
            # Update the model with iterating each text
            for i in range(len(texts)):
                doc = nlp.make_doc(texts[i])
                example.append(Example.from_dict(doc, annotations[i]))
            
            # Update the model
            try:
              nlp.update(example, drop=0.5, losses=losses)
              print("Losses", losses)
            except Exception as error:
                    print(error)
                    continue


# Save the  model to directory
output_dir = Path('.\section_model')
nlp.to_disk(output_dir)
print("Saved model to", output_dir)
