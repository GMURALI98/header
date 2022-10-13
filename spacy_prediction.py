import spacy
from urllib.request import urlopen
from bs4 import BeautifulSoup
from act_dictionary import act_dic
import re

def predict(text):
    nlp = spacy.load("spacymodels/model")
    nlp_org = spacy.load("spacymodels/org_model")
    nlp_section = spacy.load("spacymodels/old_section_model")
    html_text = text

    try:   
        soup = BeautifulSoup(html_text, features="html.parser")
 
        # kill all script and style elements
        for script in soup(["script", "style"]):
            script.extract() 

        # get text
        text = soup.body.get_text()

    except:
        pass
    
    # print(text)

    doc = nlp(text)
    doc_org = nlp_org(text)
    doc_section = nlp_section(text)
    predicted_text, acts, cits, orgs = get_entities(html_text, doc, doc_org, doc_section)

    return {"Acts": acts, "Citations": cits, "Organizations": orgs, "html_text": predicted_text}

def get_entities(text, doc, doc_org, doc_section):

    path="https://www.quickcompany.in/acts/"
    acts_list = []
    cit_list = []
    org_list = []
    # acts_formatted = []
    data = " ".join(text.split())

    for ent in doc_section.ents:

        #break at acts, and take only 6 next chars of string to concatand generate key
        #replace whitespaces in act detected and compare with act dictionary
        copy = ent.text
        act = re.sub(r'<.*?>', '', ent.text)
        act_copy = act.lower()
        try: 
                
            if "act" in act_copy:
                tokens = act_copy.split("act")
                if len(tokens) > 1 and len(tokens[1]) > 3:    
                    # print(tokens)
                    act = tokens[0] + "Act" + tokens[1][:6]
                else:
                    act = tokens[0] + "Act"
                    
                # acts_list.append(act)
                section, act = act.split("of") 
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
                    a_tag = f"<a href={path}{act}#{section}>{copy}</a>"
                    acts_list.append(act)

                else:
                    pass

                data = data.replace(copy, a_tag)
        
        except:
            pass

    for ent in doc.ents:
        if ent.label_ == "CIT":
            citation = ent.text
            if ("VS." in citation )or ("V." in citation) or ("vs." in citation) or ("Vs." in citation) or ("v." in citation):
                cit_list.append(citation)

        # elif ent.label_ == "ORG":
        #     organization = ent.text
        #     if ("LIMITED" in organization) or ("LTD" in organization) or ("ltd" in organization):
        #         org_list.append(organization)

    for ent in doc_org.ents:
        if ent.label_ == "ORG":
            organization = ent.text
            if ("LIMITED" in organization) or ("LTD" in organization) or ("Ltd" in organization):
                org_list.append(organization)
            # org_list.append(organization)
    
    return data, [*{*acts_list}], [*{*cit_list}], [*{*org_list}]

        