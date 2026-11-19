"""Mark the supplied Word template's fillable blanks without rewriting its clauses/layout."""
from pathlib import Path
import re
from zipfile import ZipFile
from xml.etree import ElementTree as ET
from io import BytesIO
from copy import deepcopy

ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "G5_BE/src/main/resources/contracts"
SOURCE = RESOURCES / "BK_VTCOMS_Hop_dong_dao_tao_nghe.docx"
DESTINATION = RESOURCES / "training-contract-template.docx"
NS = {"w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main"}


def date_parts(key):
    return [key + "Day", key + "Month", key + "Year"]


def tokens(node, keys):
    """Replace dot ranges even when Word splits one blank across several text runs."""
    text = "".join(n.text or "" for n in node.findall(".//w:t", NS))
    matches = list(re.finditer(r"\.{2,}", text))
    if len(matches) != len(keys):
        raise ValueError(f"Expected {len(keys)} blanks, found {len(matches)}: {text}")
    for match, key in reversed(list(zip(matches, keys))):
        replacement = "${" + key + "}"
        offset = 0
        inserted = False
        for element in node.findall(".//w:t", NS):
            original = element.text or ""
            end = offset + len(original)
            if end > match.start() and offset < match.end():
                start_cut = max(0, match.start() - offset)
                end_cut = min(len(original), match.end() - offset)
                element.text = original[:start_cut] + (replacement if not inserted else "") + original[end_cut:]
                inserted = True
            offset = end


def full_text(node, value):
    elements = node.findall(".//w:t", NS)
    elements[0].text = value
    for element in elements[1:]:
        element.text = ""


def prepare():
    with ZipFile(SOURCE) as original:
        raw_xml = original.read("word/document.xml")
        namespaces = dict(value for _, value in ET.iterparse(BytesIO(raw_xml), events=("start-ns",)))
        for prefix, uri in namespaces.items():
            ET.register_namespace(prefix, uri)
        document = ET.fromstring(raw_xml)
        used_uris = set()
        for element in document.iter():
            for name in (element.tag, *element.attrib):
                if name.startswith("{"):
                    used_uris.add(name[1:].split("}", 1)[0])
        # Keep declarations referenced by mc:Ignorable even when no element uses that prefix.
        for prefix, uri in namespaces.items():
            if uri not in used_uris:
                document.set("xmlns:" + prefix if prefix else "xmlns", uri)
        body = document.find("w:body", NS)
        mapping = {
            10: date_parts("agreementDate") + ["agreementPlace"],
            20: ["studentName"], 21: date_parts("studentDateOfBirth"),
            22: ["studentIdentityNumber"], 23: ["studentAddress"],
            24: ["studentPhone", "studentEmail"], 25: ["studentExperience"],
            26: ["emergencyContactName", "emergencyRelationship"], 27: ["emergencyContactPhone"],
            31: ["courseName"], 36: ["durationMonths"],
            37: date_parts("startsOn") + date_parts("endsOn") + date_parts("latestOpeningOn"),
            49: ["initialTotalInWords"],
            134: ["academicContact", "accountingContact"], 135: ["equipmentContact"],
            141: date_parts("effectiveOn"),
        }
        for index, keys in mapping.items():
            tokens(body[index], keys)
        full_text(body[3], "Thời hạn đào tạo: ${durationMonths} tháng")
        # Receiving details follow the centre's current bank account. Sent PDFs are frozen.
        full_text(body[16], "Tài khoản nhận thanh toán: ${centerBankAccount}")
        full_text(body[17], "Chủ tài khoản: ${centerBankAccountHolder} Ngân hàng: ${centerBankName}")
        rows = body[47].findall("w:tr", NS)
        tokens(rows[1].findall("w:tc", NS)[1], ["tuitionDue"])
        tokens(rows[2].findall("w:tc", NS)[1], ["depositDue"])

        # Preserve existing supplementary terms, without reintroducing a payment-schedule section.
        additional_terms = deepcopy(body[148])
        full_text(additional_terms, "${additionalTerms}")
        body.insert(149, additional_terms)

        transformed = ET.tostring(document, xml_declaration=True, encoding="UTF-8")
        with ZipFile(DESTINATION, "w") as output:
            for item in original.infolist():
                output.writestr(item, transformed if item.filename == "word/document.xml" else original.read(item.filename))
    print(f"Prepared {DESTINATION.name} from the original template; all clauses and table styles retained.")


if __name__ == "__main__":
    prepare()
