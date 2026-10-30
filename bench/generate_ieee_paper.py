"""
Generate paper_ieee.docx and paper.pdf strictly following the IEEE Conference Paper Template:
- A4 size with standard IEEE margins
- Full-width title and 6-author block (arranged in grid matching template)
- Continuous section break switching to TWO COLUMNS
- Abstract and Keywords in IEEE bold/italic styling
- Roman numeral centered headings (I. INTRODUCTION, etc.)
- Italic subsection headings (A. Classical Hash Table Algorithms, etc.)
- In-column booktabs tables (Tables I - IV)
- In-column high-resolution figures (Figs. 1 - 6) with IEEE captions
- IEEE equation numbering and formatting
- IEEE standard references list
"""

import os
import sys
from docx import Document
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.enum.section import WD_SECTION
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

def set_cell_margins(cell, top=60, bottom=60, left=80, right=80):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = OxmlElement('w:tcMar')
    for m, val in [('top', top), ('bottom', bottom), ('left', left), ('right', right)]:
        node = OxmlElement(f'w:{m}')
        node.set(qn('w:w'), str(val))
        node.set(qn('w:type'), 'dxa')
        tcMar.append(node)
    tcPr.append(tcMar)

def set_cell_shading(cell, color_hex="F2F2F2"):
    shading = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{color_hex}"/>')
    cell._tc.get_or_add_tcPr().append(shading)

def set_table_borders(table, has_header=True):
    tblPr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>\n'
        f'  <w:top w:val="single" w:sz="10" w:space="0" w:color="000000"/>\n'
        f'  <w:left w:val="none"/>\n'
        f'  <w:bottom w:val="single" w:sz="10" w:space="0" w:color="000000"/>\n'
        f'  <w:right w:val="none"/>\n'
        f'  <w:insideH w:val="none"/>\n'
        f'  <w:insideV w:val="none"/>\n'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)
    
    if has_header and len(table.rows) > 1:
        header_row = table.rows[0]
        for cell in header_row.cells:
            tcPr = cell._tc.get_or_add_tcPr()
            tcBorders = parse_xml(
                f'<w:tcBorders {nsdecls("w")}>\n'
                f'  <w:bottom w:val="single" w:sz="6" w:space="0" w:color="000000"/>\n'
                f'</w:tcBorders>'
            )
            tcPr.append(tcBorders)

def add_p(doc, text="", align=WD_ALIGN_PARAGRAPH.LEFT, bold=False, italic=False, size=Pt(10), 
          space_before=Pt(0), space_after=Pt(3), indent=Inches(0), line_spacing=1.05):
    p = doc.add_paragraph()
    p.alignment = align
    p.paragraph_format.space_before = space_before
    p.paragraph_format.space_after = space_after
    p.paragraph_format.first_line_indent = indent
    p.paragraph_format.line_spacing = line_spacing
    if text:
        run = p.add_run(text)
        run.bold = bold
        run.italic = italic
        run.font.name = "Times New Roman"
        run.font.size = size
    return p

def add_heading_1(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_before = Pt(10)
    p.paragraph_format.space_after = Pt(3)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = "Times New Roman"
    run.font.size = Pt(10)
    run.bold = True
    return p

def add_heading_2(doc, text):
    p = doc.add_paragraph()
    p.alignment = WD_ALIGN_PARAGRAPH.LEFT
    p.paragraph_format.space_before = Pt(7)
    p.paragraph_format.space_after = Pt(2)
    p.paragraph_format.keep_with_next = True
    run = p.add_run(text)
    run.font.name = "Times New Roman"
    run.font.size = Pt(10)
    run.italic = True
    run.bold = True
    return p

def add_equation(doc, eq_text, eq_num):
    tbl = doc.add_table(rows=1, cols=2)
    tbl.alignment = WD_TABLE_ALIGNMENT.CENTER
    tbl.autofit = False
    
    # 2 columns inside 3.4 inch column
    c0 = tbl.cell(0, 0)
    c1 = tbl.cell(0, 1)
    c0.width = Inches(2.9)
    c1.width = Inches(0.48)
    
    p0 = c0.paragraphs[0]
    p0.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p0.paragraph_format.space_before = Pt(2)
    p0.paragraph_format.space_after = Pt(2)
    r0 = p0.add_run(eq_text)
    r0.font.name = "Times New Roman"
    r0.font.size = Pt(9)
    r0.italic = True
    
    p1 = c1.paragraphs[0]
    p1.alignment = WD_ALIGN_PARAGRAPH.RIGHT
    p1.paragraph_format.space_before = Pt(2)
    p1.paragraph_format.space_after = Pt(2)
    r1 = p1.add_run(f"({eq_num})")
    r1.font.name = "Times New Roman"
    r1.font.size = Pt(9)
    
    p_after = doc.add_paragraph()
    p_after.paragraph_format.space_before = Pt(0)
    p_after.paragraph_format.space_after = Pt(2)

def format_cell(cell, text, bold=False, italic=False, align=WD_ALIGN_PARAGRAPH.LEFT, font_size=Pt(7.5), is_header=False):
    cell.vertical_alignment = WD_ALIGN_VERTICAL.CENTER
    p = cell.paragraphs[0]
    p.alignment = align
    p.paragraph_format.space_before = Pt(1.5)
    p.paragraph_format.space_after = Pt(1.5)
    p.paragraph_format.line_spacing = 1.0
    run = p.add_run(text)
    run.font.name = "Times New Roman"
    run.font.size = font_size
    run.bold = bold
    run.italic = italic
    set_cell_margins(cell, top=40, bottom=40, left=50, right=50)
    if is_header:
        set_cell_shading(cell, "F2F2F2")

def main():
    doc = Document()
    
    # Setup page: A4
    s1 = doc.sections[0]
    s1.page_width = Inches(8.27)
    s1.page_height = Inches(11.69)
    s1.top_margin = Inches(0.75)
    s1.bottom_margin = Inches(1.0)
    s1.left_margin = Inches(0.62)
    s1.right_margin = Inches(0.62)
    
    # Default Normal style
    style = doc.styles['Normal']
    font = style.font
    font.name = 'Times New Roman'
    font.size = Pt(10)
    
    # ==================== SECTION 1: TITLE & AUTHORS (1 COLUMN) ====================
    # Title
    p_title = doc.add_paragraph()
    p_title.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_title.paragraph_format.space_before = Pt(0)
    p_title.paragraph_format.space_after = Pt(10)
    r_title = p_title.add_run("HashBench: Empirical Performance Evaluation of Separate Chaining, Linear Probing, and Robin Hood Hashing Under Dynamic Key Workloads")
    r_title.bold = True
    r_title.font.name = "Times New Roman"
    r_title.font.size = Pt(19)
    
    # Sub-title note
    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p_sub.paragraph_format.space_before = Pt(0)
    p_sub.paragraph_format.space_after = Pt(10)
    r_sub = p_sub.add_run("FPT University – Fall 2026 Semester – Class SE20C – Group 2 (Variant B)")
    r_sub.italic = True
    r_sub.font.name = "Times New Roman"
    r_sub.font.size = Pt(10)
    
    # Author Block: 4 authors in Row 1, 2 authors in Row 2 (matching template image)
    authors_r1 = [
        ("Huỳnh Anh Tuấn", "TV1: Team Leader / Runner", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE200073@fpt.edu.vn"),
        ("Hồ Anh Nhật", "TV2: SC & Hashing", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE200301@fpt.edu.vn"),
        ("Đinh Thiện Nhân", "TV3: LP-Tomb & Rebuild", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE190092@fpt.edu.vn"),
        ("Nguyễn Trần Anh Kiệt", "TV4: Test Harness & RQ2/3", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE200105@fpt.edu.vn")
    ]
    
    table_auth1 = doc.add_table(rows=1, cols=4)
    table_auth1.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_auth1.autofit = False
    col_w = Inches(1.75)
    for c_idx, cell in enumerate(table_auth1.rows[0].cells):
        cell.width = col_w
        name, role, dept, univ, email = authors_r1[c_idx]
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.05
        
        r1 = p.add_run(f"{name}\n")
        r1.bold = True
        r1.font.size = Pt(9.5)
        
        r2 = p.add_run(f"{role}\n{dept}\n{univ}\n")
        r2.font.size = Pt(8)
        r2.italic = True
        
        r3 = p.add_run(email)
        r3.font.size = Pt(8)
        set_cell_margins(cell, top=20, bottom=20, left=40, right=40)
        
    p_sp = doc.add_paragraph()
    p_sp.paragraph_format.space_before = Pt(4)
    p_sp.paragraph_format.space_after = Pt(4)
    
    # Row 2: 2 authors centered (using 4 columns with empty outer columns)
    authors_r2 = [
        ("Châu Minh Tiến", "TV5: Robin Hood & Framing", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE200077@fpt.edu.vn"),
        ("Võ Ngọc Phú", "TV6: LP-Backshift & Validator", "Dept. of Computer Science", "FPT University, Quy Nhon", "QE200004@fpt.edu.vn")
    ]
    
    table_auth2 = doc.add_table(rows=1, cols=4)
    table_auth2.alignment = WD_TABLE_ALIGNMENT.CENTER
    table_auth2.autofit = False
    for cell in table_auth2.rows[0].cells:
        cell.width = col_w
        
    for i, (name, role, dept, univ, email) in enumerate(authors_r2):
        cell = table_auth2.rows[0].cells[i + 1] # Put in middle two columns (idx 1 and 2)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p.paragraph_format.space_before = Pt(0)
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.05
        
        r1 = p.add_run(f"{name}\n")
        r1.bold = True
        r1.font.size = Pt(9.5)
        
        r2 = p.add_run(f"{role}\n{dept}\n{univ}\n")
        r2.font.size = Pt(8)
        r2.italic = True
        
        r3 = p.add_run(email)
        r3.font.size = Pt(8)
        set_cell_margins(cell, top=20, bottom=20, left=40, right=40)
        
    # ==================== SECTION 2: 2 COLUMNS (ABSTRACT TO END) ====================
    s2 = doc.add_section(WD_SECTION.CONTINUOUS)
    s2.top_margin = Inches(0.75)
    s2.bottom_margin = Inches(1.0)
    s2.left_margin = Inches(0.62)
    s2.right_margin = Inches(0.62)
    
    # Configure 2 columns in Section 2
    sectPr = s2._sectPr
    cols = sectPr.xpath('./w:cols')
    if cols:
        cols[0].set(qn('w:num'), '2')
        cols[0].set(qn('w:space'), '360') # 0.25 in
    else:
        cols = OxmlElement('w:cols')
        cols.set(qn('w:num'), '2')
        cols.set(qn('w:space'), '360')
        sectPr.append(cols)
        
    # Abstract
    p_abs = doc.add_paragraph()
    p_abs.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p_abs.paragraph_format.space_before = Pt(6)
    p_abs.paragraph_format.space_after = Pt(3)
    p_abs.paragraph_format.line_spacing = 1.05
    
    r_absh = p_abs.add_run("Abstract—")
    r_absh.bold = True
    r_absh.italic = True
    r_absh.font.name = "Times New Roman"
    r_absh.font.size = Pt(9)
    
    abs_text = (
        "We evaluate Separate Chaining (SC), Linear Probing with tombstones and optional rebuild (LP-Tomb), "
        "Linear Probing with backward-shift deletion (LP-Backshift), and Robin Hood hashing (with and without early-stop) "
        "using Java 8 and 12-character String keys. The benchmark comprises 560 isolated JVM processes across three core "
        "research questions. At load factor α = 0.50, measured probe counts for Linear Probing are 1.5009 for successful "
        "lookups and 2.5026 for unsuccessful lookups, matching Knuth's theoretical estimates within 0.1% error. "
        "In RQ1, SC exhibits lower median lookup time than LP for unsuccessful queries across all load factors; "
        "for successful lookups, LP is faster at α = 0.50 and 0.75 due to hardware cache locality, whereas SC dominates "
        "at α = 0.90 and 0.95 where LP suffers catastrophic primary clustering. In RQ2, tombstones reach 50.0% of the table "
        "after 4M delete-insert cycles, leading to empty slot exhaustion and 12 process timeouts. In RQ3 at live load 0.8 "
        "and C = 4M, median workload times are 9.413 s for LP-Tomb with rebuild (τ = 0.1), 5.901 s for LP-Backshift, "
        "6.056 s for Robin Hood, and 6.025 s for Robin Hood without early-stop. LP-Tomb incurs 23 rebuilds with maximum block "
        "latency reaching 121.106 ms, while backward-shift and Robin Hood maintain smooth block latencies under 12.7 ms. "
        "We report all raw data, sealed under tag measure-v1, along with one timing-variance outlier verified via 11 deterministic rerun samples."
    )
    r_abst = p_abs.add_run(abs_text)
    r_abst.bold = True
    r_abst.font.name = "Times New Roman"
    r_abst.font.size = Pt(9)
    
    # Keywords
    p_kw = doc.add_paragraph()
    p_kw.alignment = WD_ALIGN_PARAGRAPH.JUSTIFY
    p_kw.paragraph_format.space_before = Pt(2)
    p_kw.paragraph_format.space_after = Pt(8)
    p_kw.paragraph_format.line_spacing = 1.05
    
    r_kwh = p_kw.add_run("Keywords—")
    r_kwh.bold = True
    r_kwh.italic = True
    r_kwh.font.name = "Times New Roman"
    r_kwh.font.size = Pt(9)
    
    kw_text = "Hash Table, Linear Probing, Separate Chaining, Tombstone Lifecycle, Rebuild Dynamics, Robin Hood Hashing, Backward-shift Deletion, Cache Locality, Pointer Chasing, Java 8."
    r_kwt = p_kw.add_run(kw_text)
    r_kwt.italic = True
    r_kwt.font.name = "Times New Roman"
    r_kwt.font.size = Pt(9)
    
    # ------------------ SECTION I. INTRODUCTION ------------------
    add_heading_1(doc, "I. INTRODUCTION")
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "Hash tables serve as indispensable core data structures across systems software, database engines, "
        "and distributed computing frameworks, delivering expected O(1) amortized time complexities for insertions, "
        "deletions, and retrievals. Nevertheless, under dynamic, high-churn operating regimes characterized by prolonged "
        "sequences of interleaved deletions and insertions, classical collision resolution strategies reveal profound "
        "divergences in algorithmic probe counts, hardware cache utilization, and tail latency stability."
    )
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("The two foundational collision resolution paradigms in computer science are ")
    p.add_run("Separate Chaining").italic = True
    p.add_run(" (SC), which resolves collisions outside the primary array via linked bucket chains, and ")
    p.add_run("Linear Probing").italic = True
    p.add_run(
        " (LP), which resolves collisions by sequentially scanning contiguous cells within a flat array. "
        "In open addressing, removing a key requires marking the vacated cell with a tombstone marker (TOMB) "
        "to preserve search path integrity for previously inserted colliding elements. Over millions of delete-insert operations, "
        "tombstone accumulation gradually exhausts available empty cells (EMPTY), degrading unsuccessful lookup operations into "
        "degenerate linear scans of length O(M) and forcing costly whole-table rebuilds. To eliminate tombstones while mitigating "
        "probe sequence variances, "
    )
    p.add_run("Robin Hood hashing").italic = True
    p.add_run(" (RH) and ")
    p.add_run("backward-shift deletion").italic = True
    p.add_run(" (Knuth's Algorithm R) have emerged as premier alternatives.")
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "This paper reports the empirical findings from the HashBench experimental campaign, consisting of 560 isolated JVM "
        "benchmark executions conducted on standardized hardware (Intel Core i7-10850H, 31.8 GB RAM, Windows 11 Pro, "
        "Eclipse Adoptium Temurin JDK 1.8.0_504) specifically examining "
    )
    p.add_run("Variant B").bold = True
    p.add_run(" (12-character alphanumeric String keys). All experimental raw data have been formally sealed under Git tag ")
    r_tag = p.add_run("measure-v1")
    r_tag.font.name = "Consolas"
    p.add_run(" in ")
    r_path = p.add_run("results/raw/official/")
    r_path.font.name = "Consolas"
    p.add_run(".")
    
    # ------------------ SECTION II. BACKGROUND & RELATED WORK ------------------
    add_heading_1(doc, "II. BACKGROUND & RELATED WORK")
    
    add_heading_2(doc, "A. Classical Hash Table Algorithms")
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Separate Chaining (SC): ").bold = True
    p.add_run(
        "The table maintains M = 2^p buckets, where each bucket anchors a singly-linked list of entry nodes. "
        "Upon a collision, the new key-value pair is inserted at the head of the chain in O(1) time following a traversal "
        "to confirm key uniqueness. Under the Simple Uniform Hashing Assumption (SUHA), Knuth [1] established that the expected "
        "probe lengths for successful lookups (S_SC) and unsuccessful lookups (U_SC) satisfy:"
    )
    
    add_equation(doc, "S_SC ≈ 1 + α / 2,    U_SC ≈ α", 1)
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "where α = N / M denotes the load factor. The distinctive strength of SC is its linear degradation with respect to α "
        "and its complete immunity to clustering phenomena."
    )
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Linear Probing (LP): ").bold = True
    p.add_run(
        "All keys reside directly within a flat array of size M. The initial bucket index is computed via 64-bit Fibonacci "
        "golden-ratio multiplicative hashing:"
    )
    
    add_equation(doc, "h(k) = ⌊ ((hashCode(k) × C_ϕ) mod 2^64) / 2^(64 - p) ⌋", 2)
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("where ")
    p.add_run("C_ϕ = 0x9E3779B97F4A7C15L = ⌊2^64 / ϕ⌋")
    p.add_run(" represents the golden-ratio constant, implemented in Java as ")
    r_code = p.add_run("(int)(((long)k.hashCode() * C_PHI) >>> (64 - p))")
    r_code.font.name = "Consolas"
    r_code.font.size = Pt(8.5)
    p.add_run(
        ". Collisions are resolved by sequentially stepping to index i = (i + 1) & (M - 1). "
        "In a tombstone-free table, Knuth's classical asymptotic approximations predict:"
    )
    
    add_equation(doc, "S_LP ≈ (1/2)·(1 + 1/(1-α)),  U_LP ≈ (1/2)·(1 + 1/(1-α)^2)", 3)
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("As α → 1.0, U_LP diverges quadratically due to ")
    p.add_run("primary clustering").italic = True
    p.add_run(
        ": contiguous occupied intervals coalesce into extended chains, such that any key hashing into the cluster "
        "further extends its span.\n\n"
        "In the lp-tomb implementation, removed keys are tagged as TOMB. When the fraction of tombstones exceeds a prescribed threshold τ, "
        "a full table rebuild is triggered to rehash live keys into a pristine table; τ = ∞ designates a non-rebuilding configuration."
    )
    
    add_heading_2(doc, "B. Robin Hood Hashing & Backward-Shift Deletion")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "Robin Hood hashing [3] tracks each element's Distance from Initial Bucket (DIB). During insertion, if the incoming "
        "element exhibits a higher DIB than the occupant of the probed slot, the elements swap roles (\"taking from the rich and "
        "giving to the poor\"). This redistributes search steps evenly, truncating the variance of probe sequences. The baseline "
        "configuration (rh/base) terminates searches early when an occupant's DIB is strictly less than the search key's current DIB; "
        "rh/noearly disables early termination to evaluate conditional branch penalties.\n\n"
    )
    p.add_run("Backward-shift deletion").italic = True
    p.add_run(
        " (lp-backshift) implements Algorithm R from Knuth [1]. Upon key removal, downstream elements within the collision cluster "
        "are shifted backward to occupy the vacancy if their hash distance allows (home ≤ vacancy), continuing until an EMPTY slot "
        "or a key residing at its natural home is encountered. This eliminates tombstones entirely without requiring global table rebuilds."
    )
    
    add_heading_2(doc, "C. In-Depth Analysis of Variant B: String Hashing")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("The architectural behavior of Variant B (12-character object String keys) diverges sharply from primitive numeric keys (Variant A, 64-bit long):")
    
    vb_points = [
        ("1) O(L) Hash Cost: ", "Java calculates string hashes via Horner's rule: h_i = 31·h_(i-1) + s[i]. For L = 12, each un-cached hash demands 11 multiplications and 12 additions."),
        ("2) JDK 8 Hash Cache: ", "The String class encapsulates a private int hash field. Once computed, the cached value is returned in O(1) time. In our benchmark, build incurs full O(L) cost; present lookups reuse references (cached O(1)); absent lookups generate fresh search keys, incurring initial O(L) hash calculation."),
        ("3) String.equals() Overhead: ", "While primitive comparisons consume a single CMP instruction, String.equals() performs reference check (==), length verification, and character loop, causing CPU stalls during extended linear probe walks."),
        ("4) Heap & GC Pressure: ", "Under 64-bit compressed OOPs, each String requires 64 bytes (24B header/fields + 40B char[12]). In Separate Chaining, each list node introduces an additional 24B header, amplifying GC footprint.")
    ]
    for bold_pre, text in vb_points:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    add_heading_2(doc, "D. Hardware Architectural Trade-offs")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "Open-addressing schemes (LP, RH) allocate continuous flat arrays. When the CPU accesses memory, the memory controller "
        "fetches an entire 64-byte Cache Line into L1/L2 caches. Sequential probing (idx + 1) triggers hardware prefetchers, executing "
        "at near-register latency. In contrast, Separate Chaining scatters Node allocations across the heap. Traversing bucket chains "
        "forces random memory indirections ("
    )
    p.add_run("pointer chasing").italic = True
    p.add_run("), generating frequent CPU cache misses.")
    
    # TABLE I (In Column)
    p_tab1 = add_p(doc, "TABLE I. MAPPING OF ALGORITHMIC MECHANISMS", 
                   align=WD_ALIGN_PARAGRAPH.CENTER, bold=True, size=Pt(8.5), space_before=Pt(6), space_after=Pt(3))
    
    table1 = doc.add_table(rows=12, cols=2)
    table1.alignment = WD_TABLE_ALIGNMENT.CENTER
    table1.autofit = False
    
    col_w1 = [Inches(1.5), Inches(1.88)]
    for row in table1.rows:
        for idx, width in enumerate(col_w1):
            row.cells[idx].width = width
            
    headers1 = ["Algorithmic Mechanism", "Class & Method"]
    for idx, h in enumerate(headers1):
        format_cell(table1.cell(0, idx), h, bold=True, align=WD_ALIGN_PARAGRAPH.LEFT, font_size=Pt(7.5), is_header=True)
        
    data1 = [
        ("Separate Chaining", "SeparateChainingTable.put/get"),
        ("LP Tombstone", "LinearProbingTombTable.put/get"),
        ("Rebuild at Threshold τ", "LinearProbingTombTable.rebuild"),
        ("LP Backward-Shift", "LinearProbingBackshiftTable.remove"),
        ("Robin Hood DIB Swapping", "RobinHoodTable.put"),
        ("RH Early-Stop", "RobinHoodTable.get (base)"),
        ("RH No-Early-Stop", "RobinHoodTable.get (noearly)"),
        ("Base-36 Key Encoding", "KeyCodec.encode/decode"),
        ("Key Deduplication", "LongSet.add/contains"),
        ("Static SHA-256 Checksum", "Generator.computeSHA256"),
        ("O(1) Workload Engine", "Workload.execute/generate"),
    ]
    for r_idx, (m, impl) in enumerate(data1, 1):
        format_cell(table1.cell(r_idx, 0), m, font_size=Pt(7.2))
        format_cell(table1.cell(r_idx, 1), impl, font_size=Pt(7.0))
        table1.cell(r_idx, 1).paragraphs[0].runs[0].font.name = "Consolas"
        
    set_table_borders(table1)
    
    # ------------------ SECTION III. METHODS & SYSTEM DESIGN ------------------
    add_heading_1(doc, "III. METHODS & SYSTEM DESIGN")
    
    add_heading_2(doc, "A. Technical Specifications & Interface Conventions")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "All hash table implementations implement the common HashIndex interface and adhere to the sentinel return convention "
    )
    r_sent = p.add_run("NOT_FOUND = Integer.MIN_VALUE")
    r_sent.font.name = "Consolas"
    r_sent.font.size = Pt(8.5)
    p.add_run(". Table I outlines the mapping between algorithmic concepts and codebase entities.")
    
    add_heading_2(doc, "B. Deterministic Data Generation & Validation")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("The benchmark employs Generator driven by SplittableRandom across three officially designated seeds: s1 = 1, s2 = 2, and s3 = 3.")
    
    gen_items = [
        ("• Key Representation: ", "KeyCodec encodes numeric 64-bit identifiers into 12-character Base-36 strings drawn from [0-9A-Z] (key space |S| = 36^12 ≈ 4.74 × 10^18)."),
        ("• Deduplication: ", "A primitive, unboxed LongSet filters duplicate keys in O(1) time, guaranteeing strictly disjoint insert and query sets."),
        ("• Static SHA-256 Checksum: ", "Generator.computeSHA256 digests ASCII bytes in 8 KB buffers with static lookup table bytesToHex, bypassing String.format.")
    ]
    for bold_pre, text in gen_items:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    add_heading_2(doc, "C. O(1) Workload Engine for Delete-Insert Cycles")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "In RQ2 and RQ3, workloads execute up to C = 4M = 4,194,304 delete-insert cycles. Naive key selection using toArray() "
        "incurs an O(N) penalty per cycle, which would inevitably trigger execution timeouts.\n\n"
        "To ensure strict O(1) performance, Workload maintains a dynamic live index array liveIdx[N]. When evicting an element "
        "at random index r, the algorithm swaps element r with the terminal element of liveIdx and decrements the active boundary "
        "in O(1) time. Furthermore, the global key pool is pre-allocated with capacity [0, N + C + Q), ensuring guaranteed key novelty "
        "without triggering memory allocations during the measurement window."
    )
    
    # ------------------ SECTION IV. EXPERIMENTAL SETUP ------------------
    add_heading_1(doc, "IV. EXPERIMENTAL SETUP")
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Hardware Specifications: ").bold = True
    p.add_run(
        "Intel Core i7-10850H CPU (6 cores / 12 threads, 2.70 GHz base frequency, 12 MB Smart Cache), 31.8 GB DDR4 RAM, "
        "64-bit Windows 11 Pro. JVM: Eclipse Adoptium Temurin JDK 1.8.0_504 with maximum heap -Xmx2g and parallel garbage collection "
        "enabled (-XX:+UseParallelGC)."
    )
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Isolated Execution Protocol: ").bold = True
    p.add_run(
        "Each benchmark configuration executes inside an isolated JVM process spawned via ProcessBuilder under an uncompromising "
        "60-second execution cutoff (waitFor(60, SECONDS)). Before measurement, each process performs 3 warm-up rounds over smaller "
        "key batches and invokes explicit System.gc() calls to stabilize heap occupancy.\n\n"
        "Timing runs (elapsed_ms) and probe-counting runs (--count) are strictly segregated; during --count runs, internal probe "
        "instrumentation is activated to capture ops_count. Full operational correctness is verified across 100% of executions by "
        "validating the output_check SHA-256 digest against an oracle baseline produced by java.util.HashMap."
    )
    
    # ------------------ SECTION V. RESULTS & DISCUSSION ------------------
    add_heading_1(doc, "V. RESULTS & DISCUSSION")
    
    add_heading_2(doc, "A. RQ1: Separate Chaining vs. Linear Probing")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "Experiment 1A assesses sensitivity to load factors α ∈ {0.50, 0.75, 0.90, 0.95} at capacity M = 2^20 = 1,048,576 "
        "(each data point represents the median of 9 valid repetitions, normalized to ns/query). Experiment 1B examines scaling across "
        "table capacities M ∈ {2^18, ..., 2^23} at fixed α = 0.75."
    )
    
    # TABLE II
    p_tab2 = add_p(doc, "TABLE II. MEDIAN LOOKUP TIMES (NS/QUERY) ACROSS α (M=2^20)", 
                   align=WD_ALIGN_PARAGRAPH.CENTER, bold=True, size=Pt(8.5), space_before=Pt(6), space_after=Pt(3))
    table2 = doc.add_table(rows=5, cols=5)
    table2.alignment = WD_TABLE_ALIGNMENT.CENTER
    table2.autofit = False
    
    col_w2 = [Inches(0.48), Inches(0.72), Inches(0.72), Inches(0.73), Inches(0.73)]
    for row in table2.rows:
        for idx, width in enumerate(col_w2):
            row.cells[idx].width = width
            
    headers2 = ["α", "LP pres", "SC pres", "LP abs", "SC abs"]
    for idx, h in enumerate(headers2):
        format_cell(table2.cell(0, idx), h, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT if idx > 0 else WD_ALIGN_PARAGRAPH.CENTER, font_size=Pt(7.5), is_header=True)
        
    data2 = [
        ("0.50", "219.7*", "246.0", "160.8", "136.3*"),
        ("0.75", "287.6*", "310.4", "407.9", "172.3*"),
        ("0.90", "407.3", "325.1*", "1861.8", "188.8*"),
        ("0.95", "596.6", "329.0*", "6971.3", "195.0*"),
    ]
    for r_idx, row_vals in enumerate(data2, 1):
        for c_idx, val in enumerate(row_vals):
            is_winner = val.endswith("*")
            display_val = val.rstrip("*")
            format_cell(table2.cell(r_idx, c_idx), display_val, bold=is_winner, 
                        align=WD_ALIGN_PARAGRAPH.RIGHT if c_idx > 0 else WD_ALIGN_PARAGRAPH.CENTER, font_size=Pt(7.5))
            
    set_table_borders(table2)
    p_note = add_p(doc, "* Bold indicates lowest latency in category.", 
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(7.5), space_before=Pt(1), space_after=Pt(4))
    
    # FIGURES 1, 2, 3
    fig_dir = r"C:\Users\ADMIN\.gemini\antigravity\scratch\Group2_CSD201_Project\results\figures"
    fig_w = Inches(3.38) # Exact width for 1 column
    
    # Fig 1
    doc.add_picture(os.path.join(fig_dir, "rq1_time_vs_alpha.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap1 = add_p(doc, "Fig. 1. RQ1: Median lookup latency (log scale) across load factors α.",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap1.runs[0].bold = True
    
    # Fig 2
    doc.add_picture(os.path.join(fig_dir, "rq1_time_vs_M.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap2 = add_p(doc, "Fig. 2. RQ1: Lookup and build latencies vs. table scale p (M=2^p, α=0.75).",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap2.runs[0].bold = True
    
    # Fig 3
    doc.add_picture(os.path.join(fig_dir, "rq1_probes_vs_alpha.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap3 = add_p(doc, "Fig. 3. RQ1: Measured probe counts vs. Knuth's theoretical estimates.",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap3.runs[0].bold = True
    
    # Detailed discussion RQ1
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Validation of Theoretical Probe Counts: ").bold = True
    p.add_run("Empirical measurements from --count runs (Fig. 3) exhibit near-perfect alignment with Knuth's equations:")
    
    probe_items = [
        ("• lp-tomb at α = 0.50: ", "successful lookups average 1.5009 probes (Knuth prediction: 1.5000, error: 0.06%), while unsuccessful lookups average 2.5026 probes (Knuth prediction: 2.5000, error: 0.10%). At α = 0.95, unsuccessful probes surge to 205.76 (Knuth prediction: 200.50)."),
        ("• sc at α = 0.50: ", "successful lookups average 1.2502 nodes (Knuth: 1 + α / 2 = 1.2500), and unsuccessful lookups average 0.5002 nodes (Knuth: α = 0.5000). At α = 0.95, successful lookups require only 1.4752 nodes and unsuccessful lookups inspect 0.9500 nodes.")
    ]
    for bold_pre, text in probe_items:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Crossover Analysis & Architectural Impact: ").bold = True
    
    cross_items = [
        ("• Moderate Load (α ≤ 0.75): ", "For present lookups, LP outperforms SC by 7% to 11% (219.7 ns vs. 246.0 ns at α = 0.50). This confirms that the superior spatial locality of contiguous arrays fully compensates for higher algorithmic probe steps, whereas SC is penalized by CPU cache misses during pointer chasing across the heap."),
        ("• High Load (α ≥ 0.90): ", "A performance crossover occurs at α ≈ 0.80. At α = 0.95, primary clustering in LP triggers cascading runs of hundreds of key.equals() string comparisons, causing LP absent lookup latency to balloon to 6,971.3 ns. Conversely, SC remains immune, holding at 195.0 ns. SC executes 35.8× faster than LP!")
    ]
    for bold_pre, text in cross_items:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Impact of Table Scale (RQ1B – Fig. 2): ").bold = True
    p.add_run(
        "When M expands from 2^18 (256 KB) to 2^23 (8 MB keys, consuming hundreds of megabytes on the heap), probe counts remain constant, "
        "yet present lookup latency for both SC and LP rises from ~200 ns to ~500 ns (a 2.5× increase). This illustrates the latency cliff "
        "encountered once the active working set exceeds the processor's 12 MB L3 cache and spills into main DDR4 RAM."
    )
    
    # ------------------ RQ2 ------------------
    add_heading_2(doc, "B. RQ2: Tombstone Accumulation & Lookup Degradation")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "RQ2 evaluates dynamic delete-insert churn without table rebuilds (τ = ∞, live load 0.5, M = 2^20). "
        "Findings are summarized in Table III and Fig. 4."
    )
    
    # TABLE III
    p_tab3 = add_p(doc, "TABLE III. TOMBSTONE ACCUMULATION & LOOKUP DEGRADATION (M=2^20, τ=∞)", 
                   align=WD_ALIGN_PARAGRAPH.CENTER, bold=True, size=Pt(8.5), space_before=Pt(6), space_after=Pt(3))
    table3 = doc.add_table(rows=7, cols=5)
    table3.alignment = WD_TABLE_ALIGNMENT.CENTER
    table3.autofit = False
    
    col_w3 = [Inches(0.55), Inches(0.65), Inches(0.65), Inches(0.77), Inches(0.76)]
    for row in table3.rows:
        for idx, width in enumerate(col_w3):
            row.cells[idx].width = width
            
    headers3 = ["Cycles", "TOMB %", "EMPTY %", "LP abs", "Ctl abs"]
    for idx, h in enumerate(headers3):
        format_cell(table3.cell(0, idx), h, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT if idx > 0 else WD_ALIGN_PARAGRAPH.CENTER, font_size=Pt(7.5), is_header=True)
        
    data3 = [
        ("0", "0.0%", "50.0%", "142.4 ns", "98.8 ns"),
        ("0.25M", "18.8%", "31.2%", "210.5 ns", "104.1 ns"),
        ("0.5M", "30.2%", "19.8%", "286.8 ns", "106.8 ns"),
        ("1M", "41.9%", "8.1%", "526.5 ns", "107.1 ns"),
        ("2M", "48.6%", "1.4%", "2058.9 ns", "108.5 ns"),
        ("4M", "50.0%", "0.0%", "timeout*", "timeout*"),
    ]
    for r_idx, row_vals in enumerate(data3, 1):
        for c_idx, val in enumerate(row_vals):
            is_to = "timeout" in val
            display_val = val.rstrip("*")
            format_cell(table3.cell(r_idx, c_idx), display_val, bold=is_to, 
                        align=WD_ALIGN_PARAGRAPH.RIGHT if c_idx > 0 else WD_ALIGN_PARAGRAPH.CENTER, font_size=Pt(7.5))
            
    set_table_borders(table3)
    p_note3 = add_p(doc, "* Indicates process timed out exceeding the strict 60s cutoff.", 
                    align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(7.5), space_before=Pt(1), space_after=Pt(4))
    
    # Fig 4
    doc.add_picture(os.path.join(fig_dir, "rq2_tombstones.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap4 = add_p(doc, "Fig. 4. RQ2: Tombstone accumulation, empty slot decay, and lookup latency vs. cycles C.",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap4.runs[0].bold = True
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Probe Degradation Analysis: ").bold = True
    p.add_run(
        "As C increases from 0 to 2M, the proportion of EMPTY cells plummets from 50.0% to just 1.4%, coalescing the entire table "
        "into a continuous occupied band of live keys and tombstones. Unsuccessful lookup time under LP surges by "
    )
    p.add_run("14.5×").bold = True
    p.add_run(
        " (from 142.4 ns to 2,058.9 ns). In contrast, the freshly rebuilt control table maintains stable latency near 100 ns.\n\n"
        "At C = 4M, EMPTY cells are completely exhausted (0.0%). During the workload phase, high probe lengths push execution times "
        "to the threshold of the 60-second timeout: 5 out of 9 timed repetitions successfully completed (median runtime 48.974 s, annotated "
        "in red as 5/9 ok in Fig. 4), while 4 timed out. In the subsequent absent lookup phase, all 9 repetitions timed out (> 60 s) because "
        "with zero empty slots remaining, the open-addressing lookup loop must traverse the entire array (M = 1,048,576 slots) before concluding "
        "absence. Corresponding control phases were subsequently not started."
    )
    
    # ------------------ RQ3 ------------------
    add_heading_2(doc, "C. RQ3: Workload Comparison & Rebuild Latency Trade-offs")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "RQ3 benchmarks four collision resolution configurations under the intensive churn scenario C = 4M at live load 0.8 "
        "(N = 838,860 keys). Results are detailed in Table IV, Fig. 5, and Fig. 6."
    )
    
    # TABLE IV
    p_tab4 = add_p(doc, "TABLE IV. MEDIAN WORKLOAD DURATION AND BLOCK LATENCIES (C=4M, α=0.8)", 
                   align=WD_ALIGN_PARAGRAPH.CENTER, bold=True, size=Pt(8.5), space_before=Pt(6), space_after=Pt(3))
    table4 = doc.add_table(rows=5, cols=4)
    table4.alignment = WD_TABLE_ALIGNMENT.CENTER
    table4.autofit = False
    
    col_w4 = [Inches(1.3), Inches(0.68), Inches(0.7), Inches(0.7)]
    for row in table4.rows:
        for idx, width in enumerate(col_w4):
            row.cells[idx].width = width
            
    headers4 = ["Configuration", "Workload", "Blk med", "Blk max"]
    for idx, h in enumerate(headers4):
        format_cell(table4.cell(0, idx), h, bold=True, align=WD_ALIGN_PARAGRAPH.RIGHT if idx > 0 else WD_ALIGN_PARAGRAPH.LEFT, font_size=Pt(7.5), is_header=True)
        
    data4 = [
        ("lp-tomb (τ = 0.1)", "9.413 s", "8.288 ms", "121.106 ms*"),
        ("lp-backshift", "5.901 s*", "7.072 ms*", "9.551 ms*"),
        ("rh", "6.056 s", "7.394 ms", "11.941 ms"),
        ("rh-noearly", "6.025 s", "7.361 ms", "12.623 ms"),
    ]
    for r_idx, row_vals in enumerate(data4, 1):
        for c_idx, val in enumerate(row_vals):
            is_bold = val.endswith("*")
            display_val = val.rstrip("*")
            format_cell(table4.cell(r_idx, c_idx), display_val, bold=is_bold, 
                        align=WD_ALIGN_PARAGRAPH.RIGHT if c_idx > 0 else WD_ALIGN_PARAGRAPH.LEFT, font_size=Pt(7.2))
            if c_idx == 0:
                table4.cell(r_idx, c_idx).paragraphs[0].runs[0].font.name = "Consolas"
                table4.cell(r_idx, c_idx).paragraphs[0].runs[0].font.size = Pt(7.0)
                
    set_table_borders(table4)
    p_note4 = add_p(doc, "* Best throughput: lp-backshift; worst spike: lp-tomb (121 ms).", 
                    align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(7.5), space_before=Pt(1), space_after=Pt(4))
    
    # Fig 5
    doc.add_picture(os.path.join(fig_dir, "rq3_total_time.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap5 = add_p(doc, "Fig. 5. RQ3: Median cumulative workload runtime across algorithms and churn C.",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap5.runs[0].bold = True
    
    # Fig 6
    doc.add_picture(os.path.join(fig_dir, "rq3_blocks.png"), width=fig_w)
    doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
    doc.paragraphs[-1].paragraph_format.space_before = Pt(4)
    doc.paragraphs[-1].paragraph_format.space_after = Pt(1)
    
    p_cap6 = add_p(doc, "Fig. 6. RQ3: 10,000-op interval median & max block latencies with max/med ratio.",
                   align=WD_ALIGN_PARAGRAPH.CENTER, italic=True, size=Pt(8), space_before=Pt(0), space_after=Pt(6))
    p_cap6.runs[0].bold = True
    
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Trade-offs Between Full Rebuilds and Instant Shifting: ").bold = True
    
    rq3_items = [
        ("• Overall Throughput: ", "lp-backshift achieves the best overall throughput (5.901 s), beating lp-tomb+rebuild (9.413 s) by 37.3%. Both Robin Hood variants achieve comparable runtimes (~6.0 s). Unlike non-rebuilding LP in RQ2, all configurations with active rebuild or shifting completed 100% of runs with zero timeouts."),
        ("• Stop-the-World Latency Spikes: ", "Across 4M cycles, lp-tomb+rebuild triggers a median of 23 full-table rebuilds (expending 2,436.6 ms exclusively in tombstone purge passes). This induces severe latency spikes in blocks containing rebuild events, pushing Block Max to 121.106 ms (max/median ≈ 14.6×)."),
        ("• Predictability of Backshift & Robin Hood: ", "By reorganizing entries locally at the moment of deletion, lp-backshift and Robin Hood bound the max/median ratio below 1.7×, with peak block latencies never exceeding 12.7 ms. This delivers crucial empirical evidence for low-latency, SLA-governed systems.")
    ]
    for bold_pre, text in rq3_items:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    # ------------------ SECTION VI. THREATS TO VALIDITY ------------------
    add_heading_1(doc, "VI. THREATS TO VALIDITY")
    
    add_heading_2(doc, "A. JIT Compilation and Cold-Start Variance")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Automated validation via CsvValidator flagged a single variance outlier: configuration ")
    r_out = p.add_run("RQ2_lp-tomb_base_rq2_p20_a0p0_tinf_l0p5_c0_s1_r1")
    r_out.font.name = "Consolas"
    r_out.font.size = Pt(8.5)
    p.add_run(
        " recorded an elapsed time of 314.931 ms during ctl_present, representing 2.065× the group median (152.481 ms) "
        "and slightly exceeding the 2.0× warning threshold.\n\n"
        "An independent deterministic rerun yielded 157.419 ms (1.043× the reference median 150.893 ms) with status ok "
        "and identical SHA-256 output_check digest. This behavior aligns with JVM JIT compilation warmup jitter on the initial process "
        "repetition. In adherence to scientific integrity, the original data point is preserved in the sealed CSV without artificial filtering."
    )
    
    add_heading_2(doc, "B. Deterministic Rerun Verification (Criterion 10.1)")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "Stratified rerun tests across 11 representative benchmark configurations achieved 100% ok status and exact output_check "
        "oracle matches. Ratios of rerun elapsed time to group median strictly fell within [0.807×, 1.302×], well inside the "
        "admissible tolerance band [0.5×, 2.0×]."
    )
    
    # ------------------ SECTION VII. CONCLUSION ------------------
    add_heading_1(doc, "VII. CONCLUSION & RECOMMENDATIONS")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run("Our extensive benchmark of Variant B under 12-character String keys yields the following engineering recommendations:")
    
    concl_items = [
        ("1) No Single Dominant Algorithm: ", "Optimal hash table selection depends strictly on load factor, read-to-write ratios, and latency tolerance."),
        ("2) Adopt Separate Chaining Under High Load: ", "When table sizing cannot be predicted or memory constraints force operation at high load (α ≥ 0.85), SC is strongly recommended due to its immunity to primary clustering and tombstone decay."),
        ("3) Adopt Backward-Shift or Robin Hood for Low Latency: ", "For real-time, SLA-governed systems subject to continuous delete-insert churn, Linear Probing with backward-shift deletion and Robin Hood hashing eliminate disruptive stop-the-world rebuild pauses while improving throughput by over 37%.")
    ]
    for bold_pre, text in concl_items:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2), indent=Inches(0.1))
        p.add_run(bold_pre).bold = True
        p.add_run(text)
        
    # ------------------ ACKNOWLEDGMENT ------------------
    add_heading_1(doc, "ACKNOWLEDGMENT")
    p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, indent=Inches(0.15))
    p.add_run(
        "The authors express their sincere appreciation to the Department of Computer Science at FPT University for designing "
        "this rigorous experimental curriculum. Generative AI tools (ChatGPT, Gemini) were consulted for PowerShell scripting assistance, "
        "Python plotting pipeline optimization (analyze.py), and LaTeX structural validation. The student authors bear complete "
        "responsibility for all Java implementations, empirical benchmark datasets, and scientific analyses presented herein."
    )
    
    # ------------------ REFERENCES ------------------
    add_heading_1(doc, "REFERENCES")
    
    refs = [
        ("[1]", "D. E. Knuth, The Art of Computer Programming, Vol. 3: Sorting and Searching, 2nd ed. Reading, MA, USA: Addison-Wesley, 1998, §6.4."),
        ("[2]", "W. W. Peterson, \"Addressing for random-access storage,\" IBM Journal of Research and Development, vol. 1, no. 2, pp. 130–146, 1957."),
        ("[3]", "P. Celis, P.-Å. Larson, and J. I. Munro, \"Robin Hood hashing,\" in Proc. 26th Annu. Symp. Foundations of Computer Science (FOCS), 1985, pp. 281–288."),
        ("[4]", "R. Sedgewick and K. Wayne, Algorithms, 4th ed. Boston, MA, USA: Addison-Wesley, 2011, §3.4."),
        ("[5]", "R. Pagh and F. F. Rodler, \"Cuckoo hashing,\" Journal of Algorithms, vol. 51, no. 2, pp. 122–144, 2004."),
        ("[6]", "C. K. Yuen, \"Heuristics for dynamic structuring of hash tables,\" ACM Transactions on Database Systems, vol. 2, no. 2, pp. 140–149, 1977.")
    ]
    
    for tag, ref_text in refs:
        p = add_p(doc, align=WD_ALIGN_PARAGRAPH.JUSTIFY, space_before=Pt(1), space_after=Pt(2))
        p.paragraph_format.left_indent = Inches(0.25)
        p.paragraph_format.first_line_indent = Inches(-0.25)
        r_tag = p.add_run(f"{tag} ")
        r_tag.bold = True
        r_tag.font.size = Pt(8)
        r_ref = p.add_run(ref_text)
        r_ref.font.size = Pt(8)
        
    # Save destinations
    out_docx = r"C:\Users\ADMIN\.gemini\antigravity\scratch\Group2_CSD201_Project\paper\paper.docx"
    doc.save(out_docx)
    print(f"Successfully generated IEEE Word document: {out_docx}")

if __name__ == "__main__":
    main()
