import spacy
from urllib.request import urlopen
from bs4 import BeautifulSoup
from act_dictionary import act_dic


def load_model(text):
    nlp = spacy.load("section_model")
    # html_url = html_link
    html_text = text
    # print(html)
    soup = BeautifulSoup(html_text, features="html.parser")


    # kill all script and style elements
    for script in soup(["script", "style"]):
        script.extract()    # rip it out

    # get text
    text = soup.body.get_text()
    doc = nlp(text)
    replaced_text = replace_section_with_anchor(html_text,doc)
    return replaced_text

def replace_section_with_anchor(html_text,doc):
    # with open (html_url,'r',encoding="utf-8") as file:
    filedata = html_text
    filedata = " ".join(filedata.split())
    # text to ankhor tag conversion
    for ent in doc.ents:
        entity=ent.text.replace("\n"," ")
        # print(entity)
        l=ent.text.split()
        # print(l)
        if len(l)>1 and "/" in l[1]:
            s= l[1].split("/")
            sec=''
            # for i in s:
            #     sec+="Section "+i+"/"
        if len(l)>1:
            if len(l)>2 and '(' in l[2]:
                s = ""
                for i in range(2,len(l)):
                    if '(' in l[i]:
                        s+=l[i]
                    else:
                        break
                sec=l[0]+" "+l[1]+s
            else:
                sec=l[0]+" "+l[1]



        # print(ent.text.strip())
        path="https://www.quickcompany.in/indiacode/"
        for i in range(1,len(l)):
            if  (not any(chr.isdigit() for chr in l[i])) and l[i]!="of" and l[i]!="the" and ("/" not in l[i]) and ('(' not in l[i]):
                    act=l[i::1]
                    act=" ".join(act)
                    # print(act+"-Act")
                    break
            else:
                act="Act"
        # print(act+"-Act",sec+"-section")
        if len(act)>180:
                continue
        else:
            if len(act)>3:
                act_main = act
            if "." in act[-1]:
                act.replace('.',"")
            if "," in act[-1]:
                act= act.replace(',',"")
                # print(act)
            if act.lower()=='act':
                try:
                    act = act_main
                except:
                    act = act
            for i,j in act_dic.items():
                if act=='Code':
                    if act==i:
                        path+=j
                        break
                elif act.lower().strip()==i.lower():
                    # print(1)
                    path+=j
                    break
                elif act.lower() in i.lower():
                    path+=j
                    break
            if len(l)>1 and "/" in l[1]:
                print(sec+" "+act,"-"+entity)
                for i in s:
                    path = path+"#"+i
                    se = "Section "+i 
                    a += '<a href="'+path+'">'+se+'</a>/'
                a = a+" of "+act
                filedata = filedata.replace(entity,a) 
            else:

                print(sec+" "+act,"-"+entity)
                path=path+"#"+sec.split()[1]
                se=sec+" of "+act
                a='<a href="'+path+'">'+se+" "+'</a>'
                filedata = filedata.replace(entity,a)
    # print(filedata)
    return(filedata)


# load_model('D:\quickcompany\Spacy\testing2.html')
