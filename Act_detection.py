import spacy
from urllib.request import urlopen
from bs4 import BeautifulSoup
from act_dictionary import act_dic
import re

def load_model(text):
    nlp = spacy.load("section_model")
    # html_url = html_link
    html_text = text
    # print(html)
<<<<<<< HEAD
    try:
            
        soup = BeautifulSoup(html_text, features="html.parser")
=======
>>>>>>> 49794400fde59dfc0498fa1209bf3c80fcc6b712

    try:   
        soup = BeautifulSoup(html_text, features="html.parser")
 
        # kill all script and style elements
        for script in soup(["script", "style"]):
            script.extract()    # rip it out

        # get text
        text = soup.body.get_text()

<<<<<<< HEAD
        # kill all script and style elements
        for script in soup(["script", "style"]):
            script.extract()    # rip it out

        # get text
        text = soup.body.get_text()

    except:
        pass
        
=======
    except:
        pass

>>>>>>> 49794400fde59dfc0498fa1209bf3c80fcc6b712
    doc = nlp(text)
    output = get_acts(html_text, doc)

    return output

def get_acts(text, doc):

    path="https://www.quickcompany.in/acts/"
    acts_list = []
    acts_formatted = []
    data = " ".join(text.split())
    
    for ent in doc.ents:
        
        #break at acts, and take only 6 next chars of string to concatand generate key
        #replace whitespaces in act detected and compare with act dictionary
        act = re.sub(r'<.*?>', '', ent.text)
        act_copy = act.lower()

        if "act" in act_copy:
            tokens = act_copy.split("act")
            if len(tokens) > 1 and len(tokens[1]) > 3:    
                # print(tokens)
                act = tokens[0] + "Act" + tokens[1][:6]
            else:
                act = tokens[0] + "Act"
                
            acts_list.append(act)
    
    for index, acts in enumerate(acts_list):
        section, act = acts.split("of") 
        section = section.replace(" ", "").split("section")[1].strip()

        if "the" not in act:
            act = f"the {act}"
        act = act.strip().replace(" ", "").lower()
 

        act_dict_keys_list = list(act_dic.keys())
        temp = []
        for i in act_dict_keys_list:
            i = i.lower().replace(" ", "")
            try:    
                i = i.replace("of", "")
            except:
                pass
            temp.append(i)

        for i, a in enumerate(temp):
            if act==a:
                act = list(act_dic.values())[i]
                break
    
        if "-" in act:    
            a_tag = f"<a href={path}{act}#{section}>{doc.ents[index].text}</a>"
            acts_formatted.append(act)

        else:
            pass

        data = data.replace(doc.ents[index].text, a_tag)
        # print(a_tag)
        # print(section)
        # print(act, "\n")
    
    # return {"source_data": data, "Acts": set(acts_formatted)}
    return data, [*{*acts_formatted}]

