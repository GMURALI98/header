package org.grobid.core.document;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.StringUtils;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Node;
import nu.xom.Text;

import org.grobid.core.GrobidModels;
import org.grobid.core.data.*;
import org.grobid.core.data.Date;
import org.grobid.core.document.xml.XmlBuilderUtils;
import org.grobid.core.engines.Engine;
import org.grobid.core.engines.FullTextParser;
import org.grobid.core.engines.label.SegmentationLabels;
import org.grobid.core.engines.config.GrobidAnalysisConfig;
import org.grobid.core.engines.counters.ReferenceMarkerMatcherCounters;
import org.grobid.core.engines.label.TaggingLabel;
import org.grobid.core.engines.label.TaggingLabels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.lang.Language;
import org.grobid.core.utilities.SentenceUtilities;
import org.grobid.core.layout.BoundingBox;
import org.grobid.core.layout.GraphicObject;
import org.grobid.core.layout.LayoutToken;
import org.grobid.core.layout.LayoutTokenization;
import org.grobid.core.layout.Page;
import org.grobid.core.tokenization.TaggingTokenCluster;
import org.grobid.core.tokenization.TaggingTokenClusteror;
import org.grobid.core.utilities.*;
import org.grobid.core.utilities.counters.CntManager;
import org.grobid.core.utilities.matching.EntityMatcherException;
import org.grobid.core.utilities.matching.ReferenceMarkerMatcher;
import org.grobid.core.engines.citations.CalloutAnalyzer.MarkerType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.*;

import static org.grobid.core.document.xml.XmlBuilderUtils.teiElement;
import static org.grobid.core.document.xml.XmlBuilderUtils.addXmlId;
import static org.grobid.core.document.xml.XmlBuilderUtils.textNode;

/**
 * Class for generating a TEI representation of a document.
 *
 */
@SuppressWarnings("StringConcatenationInsideStringBuilderAppend")
public class TEIFormatter {
    private static final Logger LOGGER = LoggerFactory.getLogger(TEIFormatter.class);

    private Document doc = null;
    private FullTextParser fullTextParser = null;
    public static final Set<TaggingLabel> MARKER_LABELS = Sets.newHashSet(
            TaggingLabels.CITATION_MARKER,
            TaggingLabels.FIGURE_MARKER,
            TaggingLabels.TABLE_MARKER,
            TaggingLabels.EQUATION_MARKER);

    // possible association to Grobid customised TEI schemas: DTD, XML schema, RelaxNG or compact RelaxNG
    // DEFAULT means no schema association in the generated XML documents
    public enum SchemaDeclaration {
        DEFAULT, DTD, XSD, RNG, RNC
    }

    private Boolean inParagraph = false;

    private ArrayList<String> elements = null;

    // static variable for the position of italic and bold features in the CRF model
    private static final int ITALIC_POS = 16;
    private static final int BOLD_POS = 15;

    private static Pattern numberRef = Pattern.compile("(\\[|\\()\\d+\\w?(\\)|\\])");
    private static Pattern numberRefCompact =
            Pattern.compile("(\\[|\\()((\\d)+(\\w)?(\\-\\d+\\w?)?,\\s?)+(\\d+\\w?)(\\-\\d+\\w?)?(\\)|\\])");
    private static Pattern numberRefCompact2 = Pattern.compile("(\\[|\\()(\\d+)(-|‒|–|—|―|\u2013)(\\d+)(\\)|\\])");

    private static Pattern startNum = Pattern.compile("^(\\d+)(.*)");

    private static final String SCHEMA_XSD_LOCATION = "https://raw.githubusercontent.com/kermitt2/grobid/master/grobid-home/schemas/xsd/Grobid.xsd";
    private static final String SCHEMA_DTD_LOCATION = "https://raw.githubusercontent.com/kermitt2/grobid/master/grobid-home/schemas/dtd/Grobid.dtd";
    private static final String SCHEMA_RNG_LOCATION = "https://raw.githubusercontent.com/kermitt2/grobid/master/grobid-home/schemas/rng/Grobid.rng";

    public TEIFormatter(Document document, FullTextParser fullTextParser) {
        this.doc = document;
        this.fullTextParser = fullTextParser;
    }

    public StringBuilder toTEIHeader(BiblioItem biblio,
                                     String defaultPublicationStatement,
                                     List<BibDataSet> bds,
                                     List<MarkerType> markerTypes,
                                     GrobidAnalysisConfig config) {
        return toTEIHeader(biblio, SchemaDeclaration.XSD, defaultPublicationStatement, bds, markerTypes, config);
    }

    public StringBuilder toTEIHeader(BiblioItem biblio,
                                     SchemaDeclaration schemaDeclaration,
                                     String defaultPublicationStatement,
                                     List<BibDataSet> bds,
                                     List<MarkerType> markerTypes,
                                     GrobidAnalysisConfig config) {
        StringBuilder tei = new StringBuilder();
        tei.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        if (config.isWithXslStylesheet()) {
            tei.append("<?xml-stylesheet type=\"text/xsl\" href=\"../jsp/xmlverbatimwrapper.xsl\"?> \n");
        }
        if (schemaDeclaration == SchemaDeclaration.DTD) {
            tei.append("<!DOCTYPE TEI SYSTEM \"" + SCHEMA_DTD_LOCATION + "\">\n");
        } else if (schemaDeclaration == SchemaDeclaration.XSD) {
            // XML schema
            tei.append("<TEI xml:space=\"preserve\" xmlns=\"http://www.tei-c.org/ns/1.0\" \n" +
                    "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" \n" +
                    "xsi:schemaLocation=\"http://www.tei-c.org/ns/1.0 " +
                    SCHEMA_XSD_LOCATION +
                    "\"\n xmlns:xlink=\"http://www.w3.org/1999/xlink\">\n");
//				"\n xmlns:mml=\"http://www.w3.org/1998/Math/MathML\">\n");
        } else if (schemaDeclaration == SchemaDeclaration.RNG) {
            // standard RelaxNG
            tei.append("<?xml-model href=\"" + SCHEMA_RNG_LOCATION +
                    "\" schematypens=\"http://relaxng.org/ns/structure/1.0\"?>\n");
        } 

        // by default there is no schema association
        if (schemaDeclaration != SchemaDeclaration.XSD) {
            tei.append("<TEI xml:space=\"preserve\" xmlns=\"http://www.tei-c.org/ns/1.0\">\n");
        }

        if (doc.getLanguage() != null) {
            tei.append("\t<teiHeader xml:lang=\"" + doc.getLanguage() + "\">");
        } else {
            tei.append("\t<teiHeader>");
        }

        tei.append("\n\t\t<fileDesc>\n\t\t\t<titleStmt>\n\t\t\t\t<title level=\"a\" type=\"main\"");
        if (config.isGenerateTeiIds()) {
            String divID = KeyGen.getKey().substring(0, 7);
            tei.append(" xml:id=\"_" + divID + "\"");
        }
        tei.append(">");

        if (biblio == null) {
            // if the biblio object is null, we simply create an empty one
            biblio = new BiblioItem();
        }

        if (biblio.getTitle() != null) {
            tei.append(TextUtilities.HTMLEncode(biblio.getTitle()));
        }

        tei.append("</title>\n\t\t\t</titleStmt>\n");


        if (biblio.getCourt() != null) {
            tei.append("\n\t<court>" +
                    TextUtilities.HTMLEncode(biblio.getCourt()) + "</court>\n");
        }

        // if (biblio.getCaseNumber() != null) {
        //     tei.append("\n\t<case_number>" +
        //             TextUtilities.HTMLEncode(biblio.getCaseNumber()) + "</case_number>\n");
        // }
        
        if (biblio.getCasedate() != null) {
            tei.append("\n\t<judgement_date>" +
                    TextUtilities.HTMLEncode(biblio.getCasedate()) + "</judgement_date>\n");
        }
        
        if (biblio.getJudge() != null) {
            tei.append("\n\t<judge>" +
                    TextUtilities.HTMLEncode(biblio.getJudge()) + "</judge>\n");
        }
        
        if (biblio.getCasetype() != null) {
            tei.append("\n\t<document_type>" +
                    TextUtilities.HTMLEncode(biblio.getCasetype()) + "</document_type>\n");
        }
        
        if (biblio.getExtra() != null) {
            tei.append("\n\t<extra>" +
                    TextUtilities.HTMLEncode(biblio.getExtra()) + "</extra>\n");
        }

        if ((biblio.getPublisher() != null) ||
                (biblio.getPublicationDate() != null) ||
                (biblio.getNormalizedPublicationDate() != null)) {
            tei.append("\t\t\t<publicationStmt>\n");
            if (biblio.getPublisher() != null) {
                // publisher and date under <publicationStmt> for better TEI conformance
                tei.append("\t\t\t\t<publisher>" + TextUtilities.HTMLEncode(biblio.getPublisher()) +
                        "</publisher>\n");

                tei.append("\t\t\t\t<availability status=\"unknown\">");
                tei.append("<p>Copyright ");
                //if (biblio.getPublicationDate() != null)
                tei.append(TextUtilities.HTMLEncode(biblio.getPublisher()) + "</p>\n");
                tei.append("\t\t\t\t</availability>\n");
            } else {
                // a dummy publicationStmt is still necessary according to TEI
                tei.append("\t\t\t\t<publisher/>\n");
                if (defaultPublicationStatement == null) {
                    tei.append("\t\t\t\t<availability status=\"unknown\"><licence/></availability>");
                } else {
                    tei.append("\t\t\t\t<availability status=\"unknown\"><p>" +
                            TextUtilities.HTMLEncode(defaultPublicationStatement) + "</p></availability>");
                }
                tei.append("\n");
            }

            if (biblio.getNormalizedPublicationDate() != null) {
                Date date = biblio.getNormalizedPublicationDate();

                String when = Date.toISOString(date);
                if (StringUtils.isNotBlank(when)) {
                    tei.append("\t\t\t\t<date type=\"published\" when=\"");
                    tei.append(when).append("\">");
                } else {
                    tei.append("\t\t\t\t<date>");
                }
                
                if (biblio.getPublicationDate() != null) {
                    tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate()));
                } else {
                    tei.append(when);
                }
                tei.append("</date>\n");
            } else if ((biblio.getYear() != null) && (biblio.getYear().length() > 0)) {
                String when = "";
                if (biblio.getYear().length() == 1)
                    when += "000" + biblio.getYear();
                else if (biblio.getYear().length() == 2)
                    when += "00" + biblio.getYear();
                else if (biblio.getYear().length() == 3)
                    when += "0" + biblio.getYear();
                else if (biblio.getYear().length() == 4)
                    when += biblio.getYear();

                if ((biblio.getMonth() != null) && (biblio.getMonth().length() > 0)) {
                    if (biblio.getMonth().length() == 1)
                        when += "-0" + biblio.getMonth();
                    else
                        when += "-" + biblio.getMonth();
                    if ((biblio.getDay() != null) && (biblio.getDay().length() > 0)) {
                        if (biblio.getDay().length() == 1)
                            when += "-0" + biblio.getDay();
                        else
                            when += "-" + biblio.getDay();
                    }
                }
                tei.append("\t\t\t\t<date type=\"published\" when=\"");
                tei.append(when + "\">");
                if (biblio.getPublicationDate() != null) {
                    tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate()));
                } else {
                    tei.append(when);
                }
                tei.append("</date>\n");
            } else if (biblio.getE_Year() != null) {
                String when = "";
                if (biblio.getE_Year().length() == 1)
                    when += "000" + biblio.getE_Year();
                else if (biblio.getE_Year().length() == 2)
                    when += "00" + biblio.getE_Year();
                else if (biblio.getE_Year().length() == 3)
                    when += "0" + biblio.getE_Year();
                else if (biblio.getE_Year().length() == 4)
                    when += biblio.getE_Year();

                if (biblio.getE_Month() != null) {
                    if (biblio.getE_Month().length() == 1)
                        when += "-0" + biblio.getE_Month();
                    else
                        when += "-" + biblio.getE_Month();

                    if (biblio.getE_Day() != null) {
                        if (biblio.getE_Day().length() == 1)
                            when += "-0" + biblio.getE_Day();
                        else
                            when += "-" + biblio.getE_Day();
                    }
                }
                tei.append("\t\t\t\t<date type=\"ePublished\" when=\"");
                tei.append(when + "\">");
                if (biblio.getPublicationDate() != null) {
                    tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate()));
                } else {
                    tei.append(when);
                }
                tei.append("</date>\n");
            } else if (biblio.getPublicationDate() != null) {
                tei.append("\t\t\t\t<date type=\"published\">");
                tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate())
                        + "</date>");
            }
            tei.append("\t\t\t</publicationStmt>\n");
        } else {
            tei.append("\t\t\t<publicationStmt>\n");
            tei.append("\t\t\t\t<publisher/>\n");
            tei.append("\t\t\t\t<availability status=\"unknown\"><licence/></availability>\n");
            tei.append("\t\t\t</publicationStmt>\n");
        }
        tei.append("\t\t\t<sourceDesc>\n\t\t\t\t<biblStruct>\n\t\t\t\t\t<analytic>\n");

        // authors + affiliation
        //biblio.createAuthorSet();
        //biblio.attachEmails();
        //biblio.attachAffiliations();

        tei.append(biblio.toTEIAuthorBlock(6, config));
        tei.append(biblio.toTEIPetitionerBlock(6, config));
        tei.append(biblio.toTEIRespondentBlock(6, config));
        tei.append(biblio.toTEIPetitionerlawerBlock(6, config));
        tei.append(biblio.toTEIRespondentlawerBlock(6, config));

        // title
        String title = biblio.getTitle();
        String language = biblio.getLanguage();
        String english_title = biblio.getEnglishTitle();
        if (title != null) {
            tei.append("\t\t\t\t\t\t<title");
            /*if ( (bookTitle == null) & (journal == null) )
                    tei.append(" level=\"m\"");
		    	else */
            tei.append(" level=\"a\" type=\"main\"");

            if (config.isGenerateTeiIds()) {
                String divID = KeyGen.getKey().substring(0, 7);
                tei.append(" xml:id=\"_" + divID + "\"");
            }

            // here check the language ?
            if (english_title == null)
                tei.append(">" + TextUtilities.HTMLEncode(title) + "</title>\n");
            else
                tei.append(" xml:lang=\"" + language + "\">" + TextUtilities.HTMLEncode(title) + "</title>\n");
        }

        boolean hasEnglishTitle = false;
        boolean generateIDs = config.isGenerateTeiIds();
        if (english_title != null) {
            // here do check the language!
            LanguageUtilities languageUtilities = LanguageUtilities.getInstance();
            Language resLang = languageUtilities.runLanguageId(english_title);

            if (resLang != null) {
                String resL = resLang.getLang();
                if (resL.equals(Language.EN)) {
                    hasEnglishTitle = true;
                    tei.append("\t\t\t\t\t\t<title");
                    //if ( (bookTitle == null) & (journal == null) )
                    //	tei.append(" level=\"m\"");
                    //else 
                    tei.append(" level=\"a\"");
                    if (generateIDs) {
                        String divID = KeyGen.getKey().substring(0, 7);
                        tei.append(" xml:id=\"_" + divID + "\"");
                    }
                    tei.append(" xml:lang=\"en\">")
                            .append(TextUtilities.HTMLEncode(english_title)).append("</title>\n");
                }
            }
            // if it's not something in English, we will write it anyway as note without type at the end
        }

        tei.append("\t\t\t\t\t</analytic>\n");

        if ((biblio.getJournal() != null) ||
                (biblio.getJournalAbbrev() != null) ||
                (biblio.getISSN() != null) ||
                (biblio.getISSNe() != null) ||
                (biblio.getPublisher() != null) ||
                (biblio.getPublicationDate() != null) ||
                (biblio.getVolumeBlock() != null) ||
                (biblio.getItem() == BiblioItem.Periodical) ||
                (biblio.getItem() == BiblioItem.InProceedings) ||
                (biblio.getItem() == BiblioItem.Proceedings) ||
                (biblio.getItem() == BiblioItem.InBook) ||
                (biblio.getItem() == BiblioItem.Book) ||
                (biblio.getItem() == BiblioItem.Serie) ||
                (biblio.getItem() == BiblioItem.InCollection)) {
            tei.append("\t\t\t\t\t<monogr");
            tei.append(">\n");

            if (biblio.getJournal() != null) {
                tei.append("\t\t\t\t\t\t<title level=\"j\" type=\"main\"");
                if (generateIDs) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    tei.append(" xml:id=\"_" + divID + "\"");
                }
                tei.append(">" + TextUtilities.HTMLEncode(biblio.getJournal()) + "</title>\n");
            } else if (biblio.getBookTitle() != null) {
                tei.append("\t\t\t\t\t\t<title level=\"m\"");
                if (generateIDs) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    tei.append(" xml:id=\"_" + divID + "\"");
                }
                tei.append(">" + TextUtilities.HTMLEncode(biblio.getBookTitle()) + "</title>\n");
            }

            if (biblio.getJournalAbbrev() != null) {
                tei.append("\t\t\t\t\t\t<title level=\"j\" type=\"abbrev\">" +
                        TextUtilities.HTMLEncode(biblio.getJournalAbbrev()) + "</title>\n");
            }

            if (biblio.getISSN() != null) {
                tei.append("\t\t\t\t\t\t<idno type=\"ISSN\">" +
                        TextUtilities.HTMLEncode(biblio.getISSN()) + "</idno>\n");
            }

            if (biblio.getISSNe() != null) {
                if (!biblio.getISSNe().equals(biblio.getISSN()))
                    tei.append("\t\t\t\t\t\t<idno type=\"eISSN\">" +
                            TextUtilities.HTMLEncode(biblio.getISSNe()) + "</idno>\n");
            }

//            if (biblio.getEvent() != null) {
//                // TODO:
//            }

            // in case the booktitle corresponds to a proceedings, we can try to indicate the meeting title
            String meeting = biblio.getBookTitle();
            boolean meetLoc = false;
            if (biblio.getEvent() != null)
                meeting = biblio.getEvent();
            else if (meeting != null) {
                meeting = meeting.trim();
                for (String prefix : BiblioItem.confPrefixes) {
                    if (meeting.startsWith(prefix)) {
                        meeting = meeting.replace(prefix, "");
                        meeting = meeting.trim();
                        tei.append("\t\t\t\t\t\t<meeting>" + TextUtilities.HTMLEncode(meeting));
                        if ((biblio.getLocation() != null) || (biblio.getTown() != null) ||
                                (biblio.getCountry() != null)) {
                            tei.append(" <address>");
                            if (biblio.getTown() != null) {
                                tei.append("<settlement>" + TextUtilities.HTMLEncode(biblio.getTown()) + "</settlement>");
                            }
                            if (biblio.getCountry() != null) {
                                tei.append("<country>" + TextUtilities.HTMLEncode(biblio.getCountry()) + "</country>");
                            }
                            if ((biblio.getLocation() != null) && (biblio.getTown() == null) &&
                                    (biblio.getCountry() == null)) {
                                tei.append("<addrLine>" + TextUtilities.HTMLEncode(biblio.getLocation()) + "</addrLine>");
                            }
                            tei.append("</address>\n");
                            meetLoc = true;
                        }
                        tei.append("\t\t\t\t\t\t</meeting>\n");
                        break;
                    }
                }
            }

            if (((biblio.getLocation() != null) || (biblio.getTown() != null) ||
                    (biblio.getCountry() != null))
                    && (!meetLoc)) {
                tei.append("\t\t\t\t\t\t<meeting>");
                tei.append(" <address>");
                if (biblio.getTown() != null) {
                    tei.append(" <settlement>" + TextUtilities.HTMLEncode(biblio.getTown()) + "</settlement>");
                }
                if (biblio.getCountry() != null) {
                    tei.append(" <country>" + TextUtilities.HTMLEncode(biblio.getCountry()) + "</country>");
                }
                if ((biblio.getLocation() != null) && (biblio.getTown() == null)
                        && (biblio.getCountry() == null)) {
                    tei.append("<addrLine>" + TextUtilities.HTMLEncode(biblio.getLocation()) + "</addrLine>");
                }
                tei.append("</address>\n");
                tei.append("\t\t\t\t\t\t</meeting>\n");
            }

            String pageRange = biblio.getPageRange();

            if ((biblio.getVolumeBlock() != null) | (biblio.getPublicationDate() != null) |
                    (biblio.getNormalizedPublicationDate() != null) |
                    (pageRange != null) | (biblio.getIssue() != null) |
                    (biblio.getBeginPage() != -1) |
                    (biblio.getPublisher() != null)) {
                tei.append("\t\t\t\t\t\t<imprint>\n");

                if (biblio.getPublisher() != null) {
                    tei.append("\t\t\t\t\t\t\t<publisher>" + TextUtilities.HTMLEncode(biblio.getPublisher())
                            + "</publisher>\n");
                }

                if (biblio.getVolumeBlock() != null) {
                    String vol = biblio.getVolumeBlock();
                    vol = vol.replace(" ", "").trim();
                    tei.append("\t\t\t\t\t\t\t<biblScope unit=\"volume\">" +
                            TextUtilities.HTMLEncode(vol) + "</biblScope>\n");
                }

                if (biblio.getIssue() != null) {
                    tei.append("\t\t\t\t\t\t\t<biblScope unit=\"issue\">"
                            + TextUtilities.HTMLEncode(biblio.getIssue()) + "</biblScope>\n");
                }

                if (pageRange != null) {
                    StringTokenizer st = new StringTokenizer(pageRange, "--");
                    if (st.countTokens() == 2) {
                        tei.append("\t\t\t\t\t\t\t<biblScope unit=\"page\"");
                        tei.append(" from=\"" + TextUtilities.HTMLEncode(st.nextToken()) + "\"");
                        tei.append(" to=\"" + TextUtilities.HTMLEncode(st.nextToken()) + "\"/>\n");
                        //tei.append(">" + TextUtilities.HTMLEncode(pageRange) + "</biblScope>\n");
                    } else {
                        tei.append("\t\t\t\t\t\t\t<biblScope unit=\"page\">" + TextUtilities.HTMLEncode(pageRange)
                                + "</biblScope>\n");
                    }
                } else if (biblio.getBeginPage() != -1) {
                    if (biblio.getEndPage() != -1) {
                        tei.append("\t\t\t\t\t\t\t<biblScope unit=\"page\"");
                        tei.append(" from=\"" + biblio.getBeginPage() + "\"");
                        tei.append(" to=\"" + biblio.getEndPage() + "\"/>\n");
                    } else {
                        tei.append("\t\t\t\t\t\t\t<biblScope unit=\"page\"");
                        tei.append(" from=\"" + biblio.getBeginPage() + "\"/>\n");
                    }
                }

                if (biblio.getNormalizedPublicationDate() != null) {
                    Date date = biblio.getNormalizedPublicationDate();

                    String when = Date.toISOString(date);
                    if (StringUtils.isNotBlank(when)) {
                        if (biblio.getPublicationDate() != null) {
                            tei.append("\t\t\t\t\t\t\t<date type=\"published\" when=\"");
                            tei.append(when + "\">");
                            tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate())
                                    + "</date>\n");
                        } else {
                            tei.append("\t\t\t\t\t\t\t<date type=\"published\" when=\"");
                            tei.append(when + "\" />\n");
                        }
                    } else {
                        if (biblio.getPublicationDate() != null) {
                            tei.append("\t\t\t\t\t\t\t<date type=\"published\">");
                            tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate())
                                    + "</date>\n");
                        }
                    }
                } else if (biblio.getYear() != null) {
                    String when = "";
                    if (biblio.getYear().length() == 1)
                        when += "000" + biblio.getYear();
                    else if (biblio.getYear().length() == 2)
                        when += "00" + biblio.getYear();
                    else if (biblio.getYear().length() == 3)
                        when += "0" + biblio.getYear();
                    else if (biblio.getYear().length() == 4)
                        when += biblio.getYear();

                    if (biblio.getMonth() != null) {
                        if (biblio.getMonth().length() == 1)
                            when += "-0" + biblio.getMonth();
                        else
                            when += "-" + biblio.getMonth();
                        if (biblio.getDay() != null) {
                            if (biblio.getDay().length() == 1)
                                when += "-0" + biblio.getDay();
                            else
                                when += "-" + biblio.getDay();
                        }
                    }
                    if (biblio.getPublicationDate() != null) {
                        tei.append("\t\t\t\t\t\t\t<date type=\"published\" when=\"");
                        tei.append(when + "\">");
                        tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate())
                                + "</date>\n");
                    } else {
                        tei.append("\t\t\t\t\t\t\t<date type=\"published\" when=\"");
                        tei.append(when + "\" />\n");
                    }
                } else if (biblio.getE_Year() != null) {
                    String when = "";
                    if (biblio.getE_Year().length() == 1)
                        when += "000" + biblio.getE_Year();
                    else if (biblio.getE_Year().length() == 2)
                        when += "00" + biblio.getE_Year();
                    else if (biblio.getE_Year().length() == 3)
                        when += "0" + biblio.getE_Year();
                    else if (biblio.getE_Year().length() == 4)
                        when += biblio.getE_Year();

                    if (biblio.getE_Month() != null) {
                        if (biblio.getE_Month().length() == 1)
                            when += "-0" + biblio.getE_Month();
                        else
                            when += "-" + biblio.getE_Month();

                        if (biblio.getE_Day() != null) {
                            if (biblio.getE_Day().length() == 1)
                                when += "-0" + biblio.getE_Day();
                            else
                                when += "-" + biblio.getE_Day();
                        }
                    }
                    tei.append("\t\t\t\t\t\t\t<date type=\"ePublished\" when=\"");
                    tei.append(when + "\" />\n");
                } else if (biblio.getPublicationDate() != null) {
                    tei.append("\t\t\t\t\t\t\t<date type=\"published\">");
                    tei.append(TextUtilities.HTMLEncode(biblio.getPublicationDate())
                            + "</date>\n");
                }

                // Fix for issue #31
                tei.append("\t\t\t\t\t\t</imprint>\n");
            }
            tei.append("\t\t\t\t\t</monogr>\n");
        } else {
            tei.append("\t\t\t\t\t<monogr>\n");
            tei.append("\t\t\t\t\t\t<imprint>\n");
            tei.append("\t\t\t\t\t\t\t<date/>\n");
            tei.append("\t\t\t\t\t\t</imprint>\n");
            tei.append("\t\t\t\t\t</monogr>\n");
        }

        if (!StringUtils.isEmpty(doc.getMD5())) {
            tei.append("\t\t\t\t\t<idno type=\"MD5\">" + doc.getMD5() + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getDOI())) {
            String theDOI = TextUtilities.HTMLEncode(biblio.getDOI());
            if (theDOI.endsWith(".xml")) {
                theDOI = theDOI.replace(".xml", "");
            }
            tei.append("\t\t\t\t\t<idno type=\"DOI\">" + TextUtilities.HTMLEncode(theDOI) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getArXivId())) {
            tei.append("\t\t\t\t\t<idno type=\"arXiv\">" + TextUtilities.HTMLEncode(biblio.getArXivId()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getPMID())) {
            tei.append("\t\t\t\t\t<idno type=\"PMID\">" + TextUtilities.HTMLEncode(biblio.getPMID()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getPMCID())) {
            tei.append("\t\t\t\t\t<idno type=\"PMCID\">" + TextUtilities.HTMLEncode(biblio.getPMCID()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getPII())) {
            tei.append("\t\t\t\t\t<idno type=\"PII\">" + TextUtilities.HTMLEncode(biblio.getPII()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getArk())) {
            tei.append("\t\t\t\t\t<idno type=\"ark\">" + TextUtilities.HTMLEncode(biblio.getArk()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getIstexId())) {
            tei.append("\t\t\t\t\t<idno type=\"istexId\">" + TextUtilities.HTMLEncode(biblio.getIstexId()) + "</idno>\n");
        }

        if (!StringUtils.isEmpty(biblio.getOAURL())) {
            tei.append("\t\t\t\t\t<ptr type=\"open-access\" target=\"").append(TextUtilities.HTMLEncode(biblio.getOAURL())).append("\" />\n");
        }

        if (biblio.getSubmission() != null) {
            tei.append("\t\t\t\t\t<note type=\"submission\">" +
                    TextUtilities.HTMLEncode(biblio.getSubmission()) + "</note>\n");
        }

        if (biblio.getDedication() != null) {
            tei.append("\t\t\t\t\t<note type=\"dedication\">" + TextUtilities.HTMLEncode(biblio.getDedication())
                    + "</note>\n");
        }

        if ((english_title != null) & (!hasEnglishTitle)) {
            tei.append("\t\t\t\t\t<note type=\"title\"");
            if (generateIDs) {
                String divID = KeyGen.getKey().substring(0, 7);
                tei.append(" xml:id=\"_" + divID + "\"");
            }
            tei.append(">" + TextUtilities.HTMLEncode(english_title) + "</note>\n");
        }

        if (biblio.getNote() != null) {
            tei.append("\t\t\t\t\t<note");
            if (generateIDs) {
                String divID = KeyGen.getKey().substring(0, 7);
                tei.append(" xml:id=\"_" + divID + "\"");
            }
            tei.append(">" + TextUtilities.HTMLEncode(biblio.getNote()) + "</note>\n");
        }

        tei.append("\t\t\t\t</biblStruct>\n");

        if (biblio.getURL() != null) {
            tei.append("\t\t\t\t<ref target=\"" + biblio.getURL() + "\" />\n");
        }

        tei.append("\t\t\t</sourceDesc>\n");
        tei.append("\t\t</fileDesc>\n");

        // encodingDesc gives info about the producer of the file
        tei.append("\t\t<encodingDesc>\n");
        tei.append("\t\t\t<appInfo>\n");

        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ");
        df.setTimeZone(tz);
        String dateISOString = df.format(new java.util.Date());

        tei.append("\t\t\t\t<application version=\"" + GrobidProperties.getVersion() +
                "\" ident=\"GROBID\" when=\"" + dateISOString + "\">\n");
        tei.append("\t\t\t\t\t<desc>GROBID - A machine learning software for extracting information from scholarly documents</desc>\n");
        tei.append("\t\t\t\t\t<ref target=\"https://github.com/kermitt2/grobid\"/>\n");
        tei.append("\t\t\t\t</application>\n");
        tei.append("\t\t\t</appInfo>\n");
        tei.append("\t\t</encodingDesc>\n");

        boolean textClassWritten = false;

        tei.append("\t\t<profileDesc>\n");
        

        if ((biblio.getCasenumbers() != null) && (biblio.getCasenumbers().size() > 0)) {
            tei.append("\t\t\t\t\t<case_number>\n");
            List<Casenumber> casenumbers = biblio.getCasenumbers();

            for (Casenumber keyw : casenumbers) {
                String res = keyw.getCasenumber().trim();
                tei.append("\t\t\t\t\t<case");
                tei.append(">" + TextUtilities.HTMLEncode(res) + "</case>\n");

            }
            tei.append("\t\t\t\t\t</case_number>\n");
        } else if (biblio.getCasenumber() != null) {
            String casenumbers = biblio.getCasenumber();
            tei.append("\t\t\t\t\t<case_number");
            tei.append(">");
            tei.append(TextUtilities.HTMLEncode(biblio.getCasenumber())).append("</case_number\n");
        }

        // keywords here !! Normally the keyword field has been preprocessed
        // if the segmentation into individual keywords worked, the first conditional
        // statement will be used - otherwise the whole keyword field is outputed
        if ((biblio.getKeywords() != null) && (biblio.getKeywords().size() > 0)) {
            textClassWritten = true;
            tei.append("\t\t\t<textClass>\n");
            tei.append("\t\t\t\t<keywords>\n");

            List<Keyword> keywords = biblio.getKeywords();
            int pos = 0;
            for (Keyword keyw : keywords) {
                if ((keyw.getKeyword() == null) || (keyw.getKeyword().length() == 0))
                    continue;
                String res = keyw.getKeyword().trim();
                if (res.startsWith(":")) {
                    res = res.substring(1);
                }
                if (pos == (keywords.size() - 1)) {
                    if (res.endsWith(".")) {
                        res = res.substring(0, res.length() - 1);
                    }
                }
                tei.append("\t\t\t\t\t<term");
                if (generateIDs) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    tei.append(" xml:id=\"_" + divID + "\"");
                }
                tei.append(">" + TextUtilities.HTMLEncode(res) + "</term>\n");
                pos++;
            }
            tei.append("\t\t\t\t</keywords>\n");
        } else if (biblio.getKeyword() != null) {
            String keywords = biblio.getKeyword();
            textClassWritten = true;
            tei.append("\t\t\t<textClass>\n");
            tei.append("\t\t\t\t<keywords");

            if (generateIDs) {
                String divID = KeyGen.getKey().substring(0, 7);
                tei.append(" xml:id=\"_" + divID + "\"");
            }
            tei.append(">");
            tei.append(TextUtilities.HTMLEncode(biblio.getKeyword())).append("</keywords>\n");
        }

        if (biblio.getCategories() != null) {
            if (!textClassWritten) {
                textClassWritten = true;
                tei.append("\t\t\t<textClass>\n");
            }
            List<String> categories = biblio.getCategories();
            tei.append("\t\t\t\t<keywords>");
            for (String category : categories) {
                tei.append("\t\t\t\t\t<term");
                if (generateIDs) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    tei.append(" xml:id=\"_" + divID + "\"");
                }
                tei.append(">" + TextUtilities.HTMLEncode(category.trim()) + "</term>\n");
            }
            tei.append("\t\t\t\t</keywords>\n");
        }

        if (textClassWritten)
            tei.append("\t\t\t</textClass>\n");

        String abstractText = biblio.getAbstract();

        Language resLang = null;
        if (abstractText != null) {
            LanguageUtilities languageUtilities = LanguageUtilities.getInstance();
            resLang = languageUtilities.runLanguageId(abstractText);
        }
        if (resLang != null) {
            String resL = resLang.getLang();
            if (!resL.equals(doc.getLanguage())) {
                tei.append("\t\t\t<abstract xml:lang=\"").append(resL).append("\">\n");
            } else {
                tei.append("\t\t\t<abstract>\n");
            }
        } else if ((abstractText == null) || (abstractText.length() == 0)) {
            tei.append("\t\t\t<abstract/>\n");
        } else {
            tei.append("\t\t\t<abstract>\n");
        }

        if ((abstractText != null) && (abstractText.length() != 0)) {
            if ( (biblio.getLabeledAbstract() != null) && (biblio.getLabeledAbstract().length() > 0) ) {
                // we have available structured abstract, which can be serialized as a full text "piece"
                StringBuilder buffer = new StringBuilder();
                try {
                    buffer = toTEITextPiece(buffer,
                                            biblio.getLabeledAbstract(),
                                            biblio,
                                            bds,
                                            false,
                                            new LayoutTokenization(biblio.getLayoutTokens(TaggingLabels.HEADER_ABSTRACT)),
                                            null, 
                                            null, 
                                            null, 
                                            markerTypes,
                                            doc,
                                            config); // no figure, no table, no equation
                } catch(Exception e) {
                    throw new GrobidException("An exception occurred while serializing TEI.", e);
                }
                tei.append(buffer.toString());
            } else {
                tei.append("\t\t\t\t<p");
                if (generateIDs) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    tei.append(" xml:id=\"_" + divID + "\"");
                }
                tei.append(">").append(TextUtilities.HTMLEncode(abstractText)).append("</p>");
            }

            tei.append("\n\t\t\t</abstract>\n");
        }

        tei.append("\t\t</profileDesc>\n");

        if ((biblio.getA_Year() != null) |
                (biblio.getS_Year() != null) |
                (biblio.getSubmissionDate() != null) |
                (biblio.getNormalizedSubmissionDate() != null)
                ) {
            tei.append("\t\t<revisionDesc>\n");
        }

        // submission and other review dates here !
        if (biblio.getA_Year() != null) {
            String when = biblio.getA_Year();
            if (biblio.getA_Month() != null) {
                when += "-" + biblio.getA_Month();
                if (biblio.getA_Day() != null) {
                    when += "-" + biblio.getA_Day();
                }
            }
            tei.append("\t\t\t\t<date type=\"accepted\" when=\"");
            tei.append(when).append("\" />\n");
        }
        if (biblio.getNormalizedSubmissionDate() != null) {
            Date date = biblio.getNormalizedSubmissionDate();
            int year = date.getYear();
            int month = date.getMonth();
            int day = date.getDay();

            String when = "" + year;
            if (month != -1) {
                when += "-" + month;
                if (day != -1) {
                    when += "-" + day;
                }
            }
            tei.append("\t\t\t\t<date type=\"submission\" when=\"");
            tei.append(when).append("\" />\n");
        } else if (biblio.getS_Year() != null) {
            String when = biblio.getS_Year();
            if (biblio.getS_Month() != null) {
                when += "-" + biblio.getS_Month();
                if (biblio.getS_Day() != null) {
                    when += "-" + biblio.getS_Day();
                }
            }
            tei.append("\t\t\t\t<date type=\"submission\" when=\"");
            tei.append(when).append("\" />\n");
        } else if (biblio.getSubmissionDate() != null) {
            tei.append("\t\t\t<date type=\"submission\">")
                    .append(TextUtilities.HTMLEncode(biblio.getSubmissionDate())).append("</date>\n");

            /*tei.append("\t\t\t<change when=\"");
            tei.append(TextUtilities.HTMLEncode(biblio.getSubmissionDate()));
			tei.append("\">Submitted</change>\n");
			*/
        }
        if ((biblio.getA_Year() != null) |
                (biblio.getS_Year() != null) |
                (biblio.getSubmissionDate() != null)
                ) {
            tei.append("\t\t</revisionDesc>\n");
        }

        tei.append("\t</teiHeader>\n");

        // output pages dimensions in the case coordinates will also be provided for some structures
        try {
            tei = toTEIPages(tei, doc, config);
        } catch(Exception e) {
            LOGGER.warn("Problem when serializing page size", e);
        }

        if (doc.getLanguage() != null) {
            tei.append("\t<text xml:lang=\"").append(doc.getLanguage()).append("\">\n");
        } else {
            tei.append("\t<text>\n");
        }

        return tei;
    }


    /**
     * TEI formatting of the body where only basic logical document structures are present.
     * This TEI format avoids most of the risks of ill-formed TEI due to structure recognition
     * errors and frequent PDF noises.
     * It is adapted to fully automatic process and simple exploitation of the document structures
     * like structured indexing and search.
     */
    public StringBuilder toTEIBody(StringBuilder buffer,
                                   String result,
                                   BiblioItem biblio,
                                   List<BibDataSet> bds,
                                   LayoutTokenization layoutTokenization,
                                   List<Figure> figures,
                                   List<Table> tables,
                                   List<Equation> equations,
                                   List<MarkerType> markerTypes,
                                   Document doc,
                                   GrobidAnalysisConfig config) throws Exception {
        if ((result == null) || (layoutTokenization == null) || (layoutTokenization.getTokenization() == null)) {
            buffer.append("\t\t<body/>\n");
            return buffer;
        }
        buffer.append("\t\t<body>\n");
        buffer = toTEITextPiece(buffer, result, biblio, bds, true, 
                layoutTokenization, figures, tables, equations, markerTypes, doc, config);

        // notes are still in the body
        buffer = toTEINote(buffer, doc, markerTypes, config);

        buffer.append("\t\t</body>\n");

        return buffer;
    }

    private StringBuilder toTEINote(StringBuilder tei,
                                    Document doc,
                                    List<MarkerType> markerTypes,
                                    GrobidAnalysisConfig config) throws Exception {
        // write the notes
        SortedSet<DocumentPiece> documentNoteParts = doc.getDocumentPart(SegmentationLabels.FOOTNOTE);
        if (documentNoteParts != null) {
            tei = toTEINote("foot", documentNoteParts, tei, markerTypes, doc, config);
        }
        documentNoteParts = doc.getDocumentPart(SegmentationLabels.MARGINNOTE);
        if (documentNoteParts != null) {
            tei = toTEINote("margin", documentNoteParts, tei, markerTypes, doc, config);
        }
        return tei;
    }

    private StringBuilder toTEINote(String noteType,
                                    SortedSet<DocumentPiece> documentNoteParts,
                                    StringBuilder tei,
                                    List<MarkerType> markerTypes,
                                    Document doc,
                                    GrobidAnalysisConfig config) throws Exception {
        List<String> allNotes = new ArrayList<>();
        for (DocumentPiece docPiece : documentNoteParts) {
            
            List<LayoutToken> noteTokens = doc.getDocumentPieceTokenization(docPiece);
            if ((noteTokens == null) || (noteTokens.size() == 0))
                continue;

            String footText = doc.getDocumentPieceText(docPiece);
            footText = TextUtilities.dehyphenize(footText);
            footText = footText.replace("\n", " ");
            footText = footText.replace("  ", " ").trim();
            if (footText.length() < 6)
                continue;
            if (allNotes.contains(footText)) {
                // basically we have here the "recurrent" headnote/footnote for each page,
                // no need to add them several times (in the future we could even use them
                // differently combined with the header)
                continue;
            }


            // pattern is <note n="1" place="foot" xml:id="no1">
            Matcher ma = startNum.matcher(footText);
            int currentNumber = -1;
            if (ma.find()) {
                String groupStr = ma.group(1);
                footText = ma.group(2);
                try {
                    currentNumber = Integer.parseInt(groupStr);
                    // remove this number from the layout tokens of the note
                    if (currentNumber != -1) {
                        String toConsume =  groupStr;
                        int start = 0;
                        for(LayoutToken token : noteTokens) {
                            if ( (token.getText() == null) || (token.getText().length() == 0) )
                                continue;
                            if (toConsume.startsWith(token.getText())) {
                                start++;
                                toConsume = toConsume.substring(token.getText().length());
                            } else
                                break;

                            if (toConsume.length() == 0)
                                break;
                        }
                        if (start != 0)
                            noteTokens = noteTokens.subList(start, noteTokens.size());

                    }
                } catch (NumberFormatException e) {
                    currentNumber = -1;
                }
            }

            //tei.append(TextUtilities.HTMLEncode(footText));
            allNotes.add(footText);

            Element desc = XmlBuilderUtils.teiElement("note");
            desc.addAttribute(new Attribute("place", noteType));
            if (currentNumber != -1) {
                desc.addAttribute(new Attribute("n", ""+currentNumber));
            }
            if (config.isGenerateTeiIds()) {
                String divID = KeyGen.getKey().substring(0, 7);
                addXmlId(desc, "_" + divID);
            }

            // for labelling bibligraphical references in footnotes 
            org.apache.commons.lang3.tuple.Pair<String, List<LayoutToken>> noteProcess = 
                fullTextParser.processShort(noteTokens, doc);
            String labeledNote = noteProcess.getLeft();
            List<LayoutToken> noteLayoutTokens = noteProcess.getRight();

            if ( (labeledNote != null) && (labeledNote.length() > 0) ) {
                TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, labeledNote, noteLayoutTokens);
                List<TaggingTokenCluster> clusters = clusteror.cluster();
                
                for (TaggingTokenCluster cluster : clusters) {
                    if (cluster == null) {
                        continue;
                    }

                    TaggingLabel clusterLabel = cluster.getTaggingLabel();
                    String clusterContent = LayoutTokensUtil.normalizeDehyphenizeText(cluster.concatTokens());
                    if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                        try {
                            List<Node> refNodes = this.markReferencesTEILuceneBased(
                                    cluster.concatTokens(),
                                    doc.getReferenceMarkerMatcher(),
                                    config.isGenerateTeiCoordinates("ref"), 
                                    false);
                            if (refNodes != null) {
                                for (Node n : refNodes) {
                                    desc.appendChild(n);
                                }
                            }
                        } catch(Exception e) {
                            LOGGER.warn("Problem when serializing TEI fragment for figure caption", e);
                        }
                    } else {
                        desc.appendChild(textNode(clusterContent));
                    }
                }
            } else {
                desc.appendChild(LayoutTokensUtil.normalizeText(footText.trim()));
            }

            tei.append("\t\t\t");
            tei.append(desc.toXML());
            tei.append("\n");
        }

        return tei;
    }

    public StringBuilder toTEIAcknowledgement(StringBuilder buffer,
                                              String reseAcknowledgement,
                                              List<LayoutToken> tokenizationsAcknowledgement,
                                              List<BibDataSet> bds,
                                              GrobidAnalysisConfig config) throws Exception {
        if ((reseAcknowledgement == null) || (tokenizationsAcknowledgement == null)) {
            return buffer;
        }

        buffer.append("\n\t\t\t<div type=\"acknowledgement\">\n");
        StringBuilder buffer2 = new StringBuilder();

        buffer2 = toTEITextPiece(buffer2, reseAcknowledgement, null, bds, false,
                new LayoutTokenization(tokenizationsAcknowledgement), null, null, null, null, doc, config);
        String acknowResult = buffer2.toString();
        String[] acknowResultLines = acknowResult.split("\n");
        boolean extraDiv = false;
        if (acknowResultLines.length != 0) {
            for (int i = 0; i < acknowResultLines.length; i++) {
                if (acknowResultLines[i].trim().length() == 0)
                    continue;
                buffer.append(TextUtilities.dehyphenize(acknowResultLines[i]) + "\n");
            }
        }
        buffer.append("\t\t\t</div>\n\n");

        return buffer;
    }


    public StringBuilder toTEIAnnex(StringBuilder buffer,
                                    String result,
                                    BiblioItem biblio,
                                    List<BibDataSet> bds,
                                    List<LayoutToken> tokenizations,
                                    List<MarkerType> markerTypes,
                                    Document doc,
                                    GrobidAnalysisConfig config) throws Exception {
        if ((result == null) || (tokenizations == null)) {
            return buffer;
        }

        buffer.append("\t\t\t<div type=\"annex\">\n");
        buffer = toTEITextPiece(buffer, result, biblio, bds, true,
                new LayoutTokenization(tokenizations), null, null, null, markerTypes, doc, config);
        buffer.append("\t\t\t</div>\n");

        return buffer;
    }

    public StringBuilder toTEITextPiece(StringBuilder buffer,
                                         String result,
                                         BiblioItem biblio,
                                         List<BibDataSet> bds,
                                         boolean keepUnsolvedCallout,
                                         LayoutTokenization layoutTokenization,
                                         List<Figure> figures,
                                         List<Table> tables,
                                         List<Equation> equations,
                                         List<MarkerType> markerTypes,
                                         Document doc,
                                         GrobidAnalysisConfig config) throws Exception {
        TaggingLabel lastClusterLabel = null;
        int startPosition = buffer.length();

        //boolean figureBlock = false; // indicate that a figure or table sequence was met
        // used for reconnecting a paragraph that was cut by a figure/table

        List<LayoutToken> tokenizations = layoutTokenization.getTokenization();

        TaggingTokenClusteror clusteror = new TaggingTokenClusteror(GrobidModels.FULLTEXT, result, tokenizations);

        String tokenLabel = null;
        List<TaggingTokenCluster> clusters = clusteror.cluster();

        List<Element> divResults = new ArrayList<>();

        Element curDiv = teiElement("div");
        if (config.isGenerateTeiIds()) {
            String divID = KeyGen.getKey().substring(0, 7);
            addXmlId(curDiv, "_" + divID);
        }
        divResults.add(curDiv);
        Element curParagraph = null;
        List<LayoutToken> curParagraphTokens = null;
        Element curList = null;
        int equationIndex = 0; // current equation index position 
        for (TaggingTokenCluster cluster : clusters) {
            if (cluster == null) {
                continue;
            }

            TaggingLabel clusterLabel = cluster.getTaggingLabel();
            Engine.getCntManager().i(clusterLabel);
            if (clusterLabel.equals(TaggingLabels.SECTION)) {
                String clusterContent = LayoutTokensUtil.normalizeDehyphenizeText(cluster.concatTokens());
                curDiv = teiElement("div");
                Element head = teiElement("head");
                // section numbers
                org.grobid.core.utilities.Pair<String, String> numb = getSectionNumber(clusterContent);
                if (numb != null) {
                    head.addAttribute(new Attribute("n", numb.b));
                    head.appendChild(numb.a);
                } else {
                    head.appendChild(clusterContent);
                }

                if (config.isGenerateTeiIds()) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    addXmlId(head, "_" + divID);
                }

                if (config.isGenerateTeiCoordinates("head") ) {
                    String coords = LayoutTokensUtil.getCoordsString(cluster.concatTokens());
                    if (coords != null) {
                        head.addAttribute(new Attribute("coords", coords));
                    }
                }

                curDiv.appendChild(head);
                divResults.add(curDiv);
            } else if (clusterLabel.equals(TaggingLabels.EQUATION) || 
                    clusterLabel.equals(TaggingLabels.EQUATION_LABEL)) {
                // get starting position of the cluster
                int start = -1;
                if ( (cluster.concatTokens() != null) && (cluster.concatTokens().size() > 0) ) {
                    start = cluster.concatTokens().get(0).getOffset();
                }
                // get the corresponding equation
                if (start != -1) {
                    Equation theEquation = null;
                    if (equations != null) {
                        for(int i=0; i<equations.size(); i++) {
                            if (i < equationIndex) 
                                continue;
                            Equation equation = equations.get(i);
                            if (equation.getStart() == start) {
                                theEquation = equation;
                                equationIndex = i;
                                break;
                            }
                        }
                        if (theEquation != null) {
                            Element element = theEquation.toTEIElement(config);
                            if (element != null)
                                curDiv.appendChild(element);
                        }
                    }
                }
            } else if (clusterLabel.equals(TaggingLabels.ITEM)) {
                String clusterContent = LayoutTokensUtil.normalizeText(cluster.concatTokens());
                //curDiv.appendChild(teiElement("item", clusterContent));
                Element itemNode = teiElement("item", clusterContent);
                if (!MARKER_LABELS.contains(lastClusterLabel) && (lastClusterLabel != TaggingLabels.ITEM)) {
                    curList = teiElement("list");
                    curDiv.appendChild(curList);
                }
                if (curList != null) {
                    curList.appendChild(itemNode);
                }
            } else if (clusterLabel.equals(TaggingLabels.OTHER)) {
                String clusterContent = LayoutTokensUtil.normalizeDehyphenizeText(cluster.concatTokens());
                Element note = teiElement("note", clusterContent);
                note.addAttribute(new Attribute("type", "other"));
                if (config.isGenerateTeiIds()) {
                    String divID = KeyGen.getKey().substring(0, 7);
                    addXmlId(note, "_" + divID);
                }
                curDiv.appendChild(note);
            } else if (clusterLabel.equals(TaggingLabels.PARAGRAPH)) {
                String clusterContent = LayoutTokensUtil.normalizeDehyphenizeText(cluster.concatTokens());
                if (isNewParagraph(lastClusterLabel, curParagraph)) {
                    if (curParagraph != null && config.isWithSentenceSegmentation()) {
                        segmentIntoSentences(curParagraph, curParagraphTokens, config, doc.getLanguage());
                    }
                    curParagraph = teiElement("p");
                    if (config.isGenerateTeiIds()) {
                        String divID = KeyGen.getKey().substring(0, 7);
                        addXmlId(curParagraph, "_" + divID);
                    }
                    curDiv.appendChild(curParagraph);
                    curParagraphTokens = new ArrayList<>();
                }
                curParagraph.appendChild(clusterContent);
                curParagraphTokens.addAll(cluster.concatTokens());
            } else if (MARKER_LABELS.contains(clusterLabel)) {
                List<LayoutToken> refTokens = cluster.concatTokens();
                refTokens = LayoutTokensUtil.dehyphenize(refTokens);
                String chunkRefString = LayoutTokensUtil.toText(refTokens);

                Element parent = curParagraph != null ? curParagraph : curDiv;
                parent.appendChild(new Text(" "));

                List<Node> refNodes;
                MarkerType citationMarkerType = null;
                if (markerTypes != null && markerTypes.size()>0) {
                    citationMarkerType = markerTypes.get(0);
                }
                if (clusterLabel.equals(TaggingLabels.CITATION_MARKER)) {
                    refNodes = markReferencesTEILuceneBased(refTokens,
                            doc.getReferenceMarkerMatcher(),
                            config.isGenerateTeiCoordinates("ref"), 
                            keepUnsolvedCallout, citationMarkerType);

                } else if (clusterLabel.equals(TaggingLabels.FIGURE_MARKER)) {
                    refNodes = markReferencesFigureTEI(chunkRefString, refTokens, figures,
                            config.isGenerateTeiCoordinates("ref"));
                } else if (clusterLabel.equals(TaggingLabels.TABLE_MARKER)) {
                    refNodes = markReferencesTableTEI(chunkRefString, refTokens, tables,
                            config.isGenerateTeiCoordinates("ref"));
                } else if (clusterLabel.equals(TaggingLabels.EQUATION_MARKER)) {
                    refNodes = markReferencesEquationTEI(chunkRefString, refTokens, equations,
                            config.isGenerateTeiCoordinates("ref"));                    
                } else {
                    throw new IllegalStateException("Unsupported marker type: " + clusterLabel);
                }

                if (refNodes != null) {
                    for (Node n : refNodes) {
                        parent.appendChild(n);
                    }
                }
                if (curParagraph != null)
                    curParagraphTokens.addAll(cluster.concatTokens());
            } else if (clusterLabel.equals(TaggingLabels.FIGURE) || clusterLabel.equals(TaggingLabels.TABLE)) {
                //figureBlock = true;
                if (curParagraph != null)
                    curParagraph.appendChild(new Text(" "));
            }

            lastClusterLabel = cluster.getTaggingLabel();
        }

        // in case we segment paragraph into sentences, we still need to do it for the last paragraph 
        if (curParagraph != null && config.isWithSentenceSegmentation()) {
            segmentIntoSentences(curParagraph, curParagraphTokens, config, doc.getLanguage());
        }

        // remove possibly empty div in the div list
        if (divResults.size() != 0) {
            for(int i = divResults.size()-1; i>=0; i--) {
                Element theDiv = divResults.get(i);
                if ( (theDiv.getChildElements() == null) || (theDiv.getChildElements().size() == 0) ) {
                    divResults.remove(i);
                }
            } 
        }

        if (divResults.size() != 0) 
            buffer.append(XmlBuilderUtils.toXml(divResults));
        else
            buffer.append(XmlBuilderUtils.toXml(curDiv));

        // we apply some overall cleaning and simplification
        buffer = TextUtilities.replaceAll(buffer, "</head><head",
                "</head>\n\t\t\t</div>\n\t\t\t<div>\n\t\t\t\t<head");
        buffer = TextUtilities.replaceAll(buffer, "</p>\t\t\t\t<p>", " ");

        //TODO: work on reconnection
        // we evaluate the need to reconnect paragraphs cut by a figure or a table
        int indP1 = buffer.indexOf("</p0>", startPosition - 1);
        while (indP1 != -1) {
            int indP2 = buffer.indexOf("<p>", indP1 + 1);
            if ((indP2 != 1) && (buffer.length() > indP2 + 5)) {
                if (Character.isUpperCase(buffer.charAt(indP2 + 4)) &&
                        Character.isLowerCase(buffer.charAt(indP2 + 5))) {
                    // a marker for reconnecting the two paragraphs
                    buffer.setCharAt(indP2 + 1, 'q');
                }
            }
            indP1 = buffer.indexOf("</p0>", indP1 + 1);
        }
        buffer = TextUtilities.replaceAll(buffer, "</p0>(\\n\\t)*<q>", " ");
        buffer = TextUtilities.replaceAll(buffer, "</p0>", "</p>");
        buffer = TextUtilities.replaceAll(buffer, "<q>", "<p>");

        if (figures != null) {
            for (Figure figure : figures) {
                String figSeg = figure.toTEI(config, doc, this, markerTypes);
                if (figSeg != null) {
                    buffer.append(figSeg).append("\n");
                }
            }
        }
        if (tables != null) {
            for (Table table : tables) {
                String tabSeg = table.toTEI(config, doc, this, markerTypes);
                if (tabSeg != null) {
                    buffer.append(tabSeg).append("\n");
                }
            }
        }

        return buffer;
    }

    public static boolean isNewParagraph(TaggingLabel lastClusterLabel, Element curParagraph) {
        return (!MARKER_LABELS.contains(lastClusterLabel) && lastClusterLabel != TaggingLabels.FIGURE
                && lastClusterLabel != TaggingLabels.TABLE) || curParagraph == null;
    }

    public void segmentIntoSentences(Element curParagraph, List<LayoutToken> curParagraphTokens, GrobidAnalysisConfig config, String lang) {
        // in order to avoid having a sentence boundary in the middle of a ref element 
        // (which is frequent given the abbreviation in the reference expression, e.g. Fig.)
        // we only consider for sentence segmentation texts under <p> and skip the text under <ref>.
        if (curParagraph == null)
            return;

        // in xom, the following gives all the text under the element, for the whole subtree
        String text = curParagraph.getValue();
        if (text == null || text.length() == 0)
            return;

        // identify ref nodes, ref spans and ref positions
        Map<Integer,Node> mapRefNodes = new HashMap<>();
        List<Integer> refPositions = new ArrayList<>();
        List<OffsetPosition> forbiddenPositions = new ArrayList<>();
        int pos = 0;
        for(int i=0; i<curParagraph.getChildCount(); i++) {
            Node theNode = curParagraph.getChild(i);
            if (theNode instanceof Text) {
                String chunk = theNode.getValue();
                pos += chunk.length();
            } else if (theNode instanceof Element) {
                // for readability in another conditional
                if (((Element) theNode).getLocalName().equals("ref")) {
                    // map character offset of the node
                    mapRefNodes.put(new Integer(pos), theNode);
                    refPositions.add(new Integer(pos));

                    String chunk = theNode.getValue();
                    forbiddenPositions.add(new OffsetPosition(pos, pos+chunk.length()));
                    pos += chunk.length();                    
                }
            }
        }

        List<OffsetPosition> theSentences = 
            SentenceUtilities.getInstance().runSentenceDetection(text, forbiddenPositions, curParagraphTokens, new Language(lang));
    
        /*if (theSentences.size() == 0) {
            // this should normally not happen, but it happens (depending on sentence splitter, usually the text 
            // is just a punctuation)
            // in this case we consider the current text as a unique sentence as fall back
            theSentences.add(new OffsetPosition(0, text.length()));
        }*/

        // segment the list of layout tokens according to the sentence segmentation if the coordinates are needed
        List<List<LayoutToken>> segmentedParagraphTokens = new ArrayList<>();
        List<LayoutToken> currentSentenceTokens = new ArrayList<>();
        pos = 0;
        
        if (config.isGenerateTeiCoordinates("s")) {
            
            int currentSentenceIndex = 0;
//System.out.println(text);            
//System.out.println("theSentences.size(): " + theSentences.size());
            String sentenceChunk = text.substring(theSentences.get(currentSentenceIndex).start, theSentences.get(currentSentenceIndex).end);

            for(int i=0; i<curParagraphTokens.size(); i++) {
                LayoutToken token = curParagraphTokens.get(i);
                if (token.getText() == null || token.getText().length() == 0) 
                    continue;
                int newPos = sentenceChunk.indexOf(token.getText(), pos);
                if ((newPos != -1) || SentenceUtilities.toSkipToken(token.getText())) {
                    // just move on
                    currentSentenceTokens.add(token);
                    if (newPos != -1 && !SentenceUtilities.toSkipToken(token.getText()))
                        pos = newPos;
                } else {
                    if (currentSentenceTokens.size() > 0) {
                        segmentedParagraphTokens.add(currentSentenceTokens);
                        currentSentenceIndex++;
                        if (currentSentenceIndex >= theSentences.size()) {
                            currentSentenceTokens = new ArrayList<>();
                            break;
                        }
                        sentenceChunk = text.substring(theSentences.get(currentSentenceIndex).start, theSentences.get(currentSentenceIndex).end);
                    }
                    currentSentenceTokens = new ArrayList<>();
                    currentSentenceTokens.add(token);
                    pos = 0;
                }
                
                if (currentSentenceIndex >= theSentences.size())
                    break;
            }
            // last sentence
            if (currentSentenceTokens.size() > 0) {
                // check sentence index too ?
                segmentedParagraphTokens.add(currentSentenceTokens);
            }

/*if (segmentedParagraphTokens.size() != theSentences.size()) {
System.out.println("ERROR, segmentedParagraphTokens size:" + segmentedParagraphTokens.size() + " vs theSentences size: " + theSentences.size());
System.out.println(text);
System.out.println(theSentences.toString());
int k = 0;
for (List<LayoutToken> segmentedParagraphToken : segmentedParagraphTokens) {
    if (k < theSentences.size())
        System.out.println(k + " sentence segmented text-only: " + text.substring(theSentences.get(k).start, theSentences.get(k).end));
    else 
        System.out.println("no text-only sentence at index " + k);
    System.out.print(k + " layout token segmented sentence: ");
    System.out.println(segmentedParagraphToken);
    k++;
}
}*/
        }

        // update the xml paragraph element
        int currenChildIndex = 0;
        pos = 0;
        int posInSentence = 0;
        int refIndex = 0;
        for(int i=0; i<theSentences.size(); i++) {
            pos = theSentences.get(i).start;
            posInSentence = 0;
            Element sentenceElement = teiElement("s");
            if (config.isGenerateTeiIds()) {
                String sID = KeyGen.getKey().substring(0, 7);
                addXmlId(sentenceElement, "_" + sID);
            }
            if (config.isGenerateTeiCoordinates("s")) {
                if (segmentedParagraphTokens.size()>=i+1) {
                    currentSentenceTokens = segmentedParagraphTokens.get(i);
                    String coords = LayoutTokensUtil.getCoordsString(currentSentenceTokens);
                    if (coords != null) {
                        sentenceElement.addAttribute(new Attribute("coords", coords));
                    }
                }
            }
            
            int sentenceLength = theSentences.get(i).end - pos;
            // check if we have a ref between pos and pos+sentenceLength
            for(int j=refIndex; j<refPositions.size(); j++) {
                int refPos = refPositions.get(j).intValue();
                if (refPos < pos+posInSentence) 
                    continue;

                if (refPos >= pos+posInSentence && refPos <= pos+sentenceLength) {
                    Node valueNode = mapRefNodes.get(new Integer(refPos));
                    if (pos+posInSentence < refPos) {
                        String local_text_chunk = text.substring(pos+posInSentence, refPos);
                        local_text_chunk = XmlBuilderUtils.stripNonValidXMLCharacters(local_text_chunk);
                        sentenceElement.appendChild(local_text_chunk);
                    }
                    valueNode.detach();
                    sentenceElement.appendChild(valueNode);
                    refIndex = j;
                    posInSentence = refPos+valueNode.getValue().length()-pos;
                }
                if (refPos > pos+sentenceLength) {
                    break;
                }
            }

            if (pos+posInSentence <= theSentences.get(i).end) {
                String local_text_chunk = text.substring(pos+posInSentence, theSentences.get(i).end);
                local_text_chunk = XmlBuilderUtils.stripNonValidXMLCharacters(local_text_chunk);
                sentenceElement.appendChild(local_text_chunk);
                curParagraph.appendChild(sentenceElement);
            }
        }

        for(int i=curParagraph.getChildCount()-1; i>=0; i--) {
            Node theNode = curParagraph.getChild(i);
            if (theNode instanceof Text) {
                curParagraph.removeChild(theNode);
            } else if (theNode instanceof Element) {
                // for readability in another conditional
                if (!((Element) theNode).getLocalName().equals("s")) {
                    curParagraph.removeChild(theNode);
                }
            }
        }

    }   

    /**
     * Return the graphic objects in a given interval position in the document.
     */
    private List<GraphicObject> getGraphicObject(List<GraphicObject> graphicObjects, int startPos, int endPos) {
        List<GraphicObject> result = new ArrayList<GraphicObject>();
        for (GraphicObject nto : graphicObjects) {
            if ((nto.getStartPosition() >= startPos) && (nto.getStartPosition() <= endPos)) {
                result.add(nto);
            }
            if (nto.getStartPosition() > endPos) {
                break;
            }
        }
        return result;
    }

    private org.grobid.core.utilities.Pair<String, String> getSectionNumber(String text) {
        Matcher m1 = BasicStructureBuilder.headerNumbering1.matcher(text);
        Matcher m2 = BasicStructureBuilder.headerNumbering2.matcher(text);
        Matcher m3 = BasicStructureBuilder.headerNumbering3.matcher(text);
        Matcher m = null;
        String numb = null;
        if (m1.find()) {
            numb = m1.group(0);
            m = m1;
        } else if (m2.find()) {
            numb = m2.group(0);
            m = m2;
        } else if (m3.find()) {
            numb = m3.group(0);
            m = m3;
        }
        if (numb != null) {
            text = text.replace(numb, "").trim();
            numb = numb.replace(" ", "");
            return new org.grobid.core.utilities.Pair<>(text, numb);
        } else {
            return null;
        }
    }

    public StringBuilder toTEIReferences(StringBuilder tei,
                                         List<BibDataSet> bds,
                                         GrobidAnalysisConfig config) throws Exception {
        tei.append("\t\t\t<div type=\"references\">\n\n");

        if ((bds == null) || (bds.size() == 0))
            tei.append("\t\t\t\t<listBibl/>\n");
        else {
            tei.append("\t\t\t\t<listBibl>\n");

            int p = 0;
            if (bds.size() > 0) {
                for (BibDataSet bib : bds) {
                    BiblioItem bit = bib.getResBib();
                    bit.setReference(bib.getRawBib());
                    if (bit != null) {
                        tei.append("\n" + bit.toTEI(p, 0, config));
                    } else {
                        tei.append("\n");
                    }
                    p++;
                }
            }
            tei.append("\n\t\t\t\t</listBibl>\n");
        }
        tei.append("\t\t\t</div>\n");

        return tei;
    }


    //bounding boxes should have already been calculated when calling this method
    public static String getCoordsAttribute(List<BoundingBox> boundingBoxes, boolean generateCoordinates) {
        if (!generateCoordinates || boundingBoxes == null || boundingBoxes.isEmpty()) {
            return "";
        }
        String coords = Joiner.on(";").join(boundingBoxes);
        return "coords=\"" + coords + "\"";
    }


    /**
     * Mark using TEI annotations the identified references in the text body build with the machine learning model.
     */
    public List<Node> markReferencesTEILuceneBased(List<LayoutToken> refTokens,
                                                   ReferenceMarkerMatcher markerMatcher, 
                                                   boolean generateCoordinates,
                                                   boolean keepUnsolvedCallout) throws EntityMatcherException {
        return markReferencesTEILuceneBased(refTokens, markerMatcher, generateCoordinates, keepUnsolvedCallout, null);
    }

    public List<Node> markReferencesTEILuceneBased(List<LayoutToken> refTokens,
                                                   ReferenceMarkerMatcher markerMatcher, 
                                                   boolean generateCoordinates,
                                                   boolean keepUnsolvedCallout,
                                                   MarkerType citationMarkerType) throws EntityMatcherException {
        // safety tests
        if ( (refTokens == null) || (refTokens.size() == 0) ) 
            return null;
        String text = LayoutTokensUtil.toText(refTokens);
        if (text == null || text.trim().length() == 0 || text.endsWith("</ref>") || text.startsWith("<ref") || markerMatcher == null)
            return Collections.<Node>singletonList(new Text(text));

        boolean spaceEnd = false;
        text = text.replace("\n", " ");
        if (text.endsWith(" "))
            spaceEnd = true;

        // check constraints on global marker type, we need to discard reference markers that do not follow the
        // reference marker pattern of the document
        if (citationMarkerType != null) {
            // do we have superscript numbers in the ref tokens?
            boolean hasSuperScriptNumber = false;
            for(LayoutToken refToken : refTokens) {
                if (refToken.isSuperscript()) {
                    hasSuperScriptNumber = true;
                    break;
                }                    
            }

            if (citationMarkerType == MarkerType.SUPERSCRIPT_NUMBER) {
                // we need to check that the reference tokens have some superscript numbers
                if (!hasSuperScriptNumber) {
                    return Collections.<Node>singletonList(new Text(text));
                }
            } else {
                // if the reference tokens has some superscript numbers, it is a callout for a different type of object
                // (e.g. a foot note)
                if (hasSuperScriptNumber) {
                    return Collections.<Node>singletonList(new Text(text));
                }
            }

            // TBD: check other constraints and consistency issues
        }

        List<Node> nodes = new ArrayList<>();
        List<ReferenceMarkerMatcher.MatchResult> matchResults = markerMatcher.match(refTokens);
        if (matchResults != null) {
            for (ReferenceMarkerMatcher.MatchResult matchResult : matchResults) {
                // no need to HTMLEncode since XOM will take care about the correct escaping
                String markerText = LayoutTokensUtil.normalizeText(matchResult.getText());
                String coords = null;
                if (generateCoordinates && matchResult.getTokens() != null) {
                    coords = LayoutTokensUtil.getCoordsString(matchResult.getTokens());
                }

                Element ref = teiElement("ref");
                ref.addAttribute(new Attribute("type", "bibr"));

                if (coords != null) {
                    ref.addAttribute(new Attribute("coords", coords));
                }
                ref.appendChild(markerText);

                boolean solved = false;
                if (matchResult.getBibDataSet() != null) {
                    ref.addAttribute(new Attribute("target", "#b" + matchResult.getBibDataSet().getResBib().getOrdinal()));
                    solved = true;
                }
                if ( solved || (!solved && keepUnsolvedCallout) )
                    nodes.add(ref);
                else 
                    nodes.add(textNode(matchResult.getText()));
            }
        }
        if (spaceEnd)
            nodes.add(new Text(" "));
        return nodes;
    }


    public List<Node> markReferencesFigureTEI(String text, 
                                            List<LayoutToken> refTokens,
                                            List<Figure> figures,
                                            boolean generateCoordinates) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        List<Node> nodes = new ArrayList<>();

        String textLow = text.toLowerCase().trim();
        String bestFigure = null;

        if (figures != null) {
            for (Figure figure : figures) {
                if ((figure.getLabel() != null) && (figure.getLabel().length() > 0)) {
                    String label = TextUtilities.cleanField(figure.getLabel(), false);
                    if ((label.length() > 0) &&
                            (textLow.equals(label.toLowerCase()))) {
                        bestFigure = figure.getId();
                        break;
                    }
                }
            }
            if (bestFigure == null) {
                // second pass with relaxed figure marker matching
                for(int i=figures.size()-1; i>=0; i--) {
                    Figure figure = figures.get(i);
                    if ((figure.getLabel() != null) && (figure.getLabel().length() > 0)) {
                        String label = TextUtilities.cleanField(figure.getLabel(), false);
                        if ((label.length() > 0) &&
                                (textLow.contains(label.toLowerCase()))) {
                            bestFigure = figure.getId();
                            break;
                        }
                    }
                }
            }
        }

        boolean spaceEnd = false;
        text = text.replace("\n", " ");
        if (text.endsWith(" "))
            spaceEnd = true;
        text = text.trim();

        String coords = null;
        if (generateCoordinates && refTokens != null) {
            coords = LayoutTokensUtil.getCoordsString(refTokens);
        }

        Element ref = teiElement("ref");
        ref.addAttribute(new Attribute("type", "figure"));

        if (coords != null) {
            ref.addAttribute(new Attribute("coords", coords));
        }
        ref.appendChild(text);

        if (bestFigure != null) {
            ref.addAttribute(new Attribute("target", "#fig_" + bestFigure));
        }
        nodes.add(ref);
        if (spaceEnd)
            nodes.add(new Text(" "));
        return nodes;
    }

    public List<Node> markReferencesTableTEI(String text, List<LayoutToken> refTokens,
                                             List<Table> tables,
                                             boolean generateCoordinates) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        List<Node> nodes = new ArrayList<>();

        String textLow = text.toLowerCase().trim();
        String bestTable = null;
        if (tables != null) {
            for (Table table : tables) {
                if ((table.getLabel() != null) && (table.getLabel().length() > 0)) {
                    String label = TextUtilities.cleanField(table.getLabel(), false);
                    if ((label.length() > 0) &&
                            (textLow.equals(label.toLowerCase()))) {
                        bestTable = table.getId();
                        break;
                    }
                }
            }

            if (bestTable == null) {
                // second pass with relaxed table marker matching
                for(int i=tables.size()-1; i>=0; i--) {
                    Table table = tables.get(i);
                    if ((table.getLabel() != null) && (table.getLabel().length() > 0)) {
                        String label = TextUtilities.cleanField(table.getLabel(), false);
                        if ((label.length() > 0) &&
                                (textLow.contains(label.toLowerCase()))) {
                            bestTable = table.getId();
                            break;
                        }
                    }
                }
            }
        }

        boolean spaceEnd = false;
        text = text.replace("\n", " ");
        if (text.endsWith(" "))
            spaceEnd = true;
        text = text.trim();

        String coords = null;
        if (generateCoordinates && refTokens != null) {
            coords = LayoutTokensUtil.getCoordsString(refTokens);
        }

        Element ref = teiElement("ref");
        ref.addAttribute(new Attribute("type", "table"));

        if (coords != null) {
            ref.addAttribute(new Attribute("coords", coords));
        }
        ref.appendChild(text);
        if (bestTable != null) {
            ref.addAttribute(new Attribute("target", "#tab_" + bestTable));
        }
        nodes.add(ref);
        if (spaceEnd)
            nodes.add(new Text(" "));
        return nodes;
    }

    private static Pattern patternNumber = Pattern.compile("\\d+");

    public List<Node> markReferencesEquationTEI(String text, 
                                            List<LayoutToken> refTokens,
                                            List<Equation> equations,
                                            boolean generateCoordinates) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }

        text = TextUtilities.cleanField(text, false);
        String textNumber = null;
        Matcher m = patternNumber.matcher(text);
        if (m.find()) {
            textNumber = m.group();
        }

        List<Node> nodes = new ArrayList<>();

        String textLow = text.toLowerCase();
        String bestFormula = null;
        if (equations != null) {
            for (Equation equation : equations) {
                if ((equation.getLabel() != null) && (equation.getLabel().length() > 0)) {
                    String label = TextUtilities.cleanField(equation.getLabel(), false);
                    Matcher m2 = patternNumber.matcher(label);
                    String labelNumber = null;
                    if (m2.find()) {
                        labelNumber = m2.group();
                    }
                    //if ((label.length() > 0) &&
                    //        (textLow.contains(label.toLowerCase()))) {
                    if ( (labelNumber != null && textNumber != null && labelNumber.length()>0 &&
                        labelNumber.equals(textNumber)) || 
                        ((label.length() > 0) && (textLow.equals(label.toLowerCase()))) ) {
                        bestFormula = equation.getId();
                        break;
                    } 
                }
            }
        }
        
        boolean spaceEnd = false;
        text = text.replace("\n", " ");
        if (text.endsWith(" "))
            spaceEnd = true;
        text = text.trim();

        String coords = null;
        if (generateCoordinates && refTokens != null) {
            coords = LayoutTokensUtil.getCoordsString(refTokens);
        }

        Element ref = teiElement("ref");
        ref.addAttribute(new Attribute("type", "formula"));

        if (coords != null) {
            ref.addAttribute(new Attribute("coords", coords));
        }
        ref.appendChild(text);
        if (bestFormula != null) {
            ref.addAttribute(new Attribute("target", "#formula_" + bestFormula));
        }
        nodes.add(ref);
        if (spaceEnd)
            nodes.add(new Text(" "));
        return nodes;
    }

    private String normalizeText(String localText) {
        localText = localText.trim();
        localText = TextUtilities.dehyphenize(localText);
        localText = localText.replace("\n", " ");
        localText = localText.replace("  ", " ");

        return localText.trim();
    }

    /**
     * In case, the coordinates of structural elements are provided in the TEI
     * representation, we need the page sizes in order to scale the coordinates 
     * appropriately. These size information are provided via the TEI facsimile 
     * element, with a surface element for each page carrying the page size info.  
     */
    public StringBuilder toTEIPages(StringBuilder buffer,
                                   Document doc,
                                   GrobidAnalysisConfig config) throws Exception {
        if (!config.isGenerateTeiCoordinates()) {
            // no cooredinates, nothing to do
            return buffer;
        }

        // page height and width
        List<Page> pages = doc.getPages();
        int pageNumber = 1;
        buffer.append("\t<facsimile>\n");
        for(Page page : pages) {
            buffer.append("\t\t<surface ");
            buffer.append("n=\"" + pageNumber + "\" "); 
            buffer.append("ulx=\"0.0\" uly=\"0.0\" ");
            buffer.append("lrx=\"" + page.getWidth() + "\" lry=\"" + page.getHeight() + "\"");
            buffer.append("/>\n");
            pageNumber++;
        }
        buffer.append("\t</facsimile>\n");

        return buffer;
    }
}