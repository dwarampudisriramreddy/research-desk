package com.ram.researchdesk

/**
 * Port of flutter_app/lib/data/curriculum.dart — BDS curriculum (Years 1–4)
 * with subject metadata filled in: blurbs, domains, default literature
 * queries, synonyms, departmental equipment and ethics watch-lists.
 */

data class ClusterSeed(
    val id: String,
    val name: String,
    val keywords: List<String> = emptyList(),
    val exposures: List<String> = emptyList(),
    val outcomes: List<String> = emptyList(),
    val methods: List<String> = emptyList(),
)

data class Year(
    val id: String,
    val numeral: String,
    val name: String,
    val kicker: String = "",
    val subjectCount: Int = 0,
)

data class Subject(
    val id: String,
    val name: String,
    val year: String,
    val blurb: String = "",
    val domains: List<String> = emptyList(),
    val defaultQuery: String = "",
    val synonyms: List<String> = emptyList(),
    val clusters: List<ClusterSeed> = emptyList(),
    val equipment: List<String> = emptyList(),
    val ethicsWatch: List<String> = emptyList(),
)

val YEARS: List<Year> = listOf(
    Year(
        id = "first",
        numeral = "I",
        name = "First BDS",
        kicker = "Foundations of the human body — normal structure and function",
    ),
    Year(
        id = "second",
        numeral = "II",
        name = "Second BDS",
        kicker = "Disease mechanisms, dental materials and drugs entering the clinic",
    ),
    Year(
        id = "third",
        numeral = "III",
        name = "Third BDS",
        kicker = "Clinical sciences — diagnosis, prevention and treatment planning",
    ),
    Year(
        id = "final",
        numeral = "IV",
        name = "Final BDS",
        kicker = "Specialty practice, community health and interdisciplinary work",
    ),
)

val SUBJECTS: List<Subject> = listOf(

    // ---------------- First BDS ----------------

    Subject(
        id = "anatomy",
        name = "General Anatomy",
        year = "first",
        blurb = "Gross anatomy with a head–neck emphasis for dental practice, plus histology and embryology.",
        domains = listOf(
            "Head & Neck Anatomy", "Cranial Nerves", "Orbit & Paranasal Sinuses",
            "TMJ & Masticatory Muscles", "Infratemporal & Pterygopalatine Fossae",
            "Oral Cavity, Tongue & Palate", "Pharynx & Larynx",
            "Thorax & Heart", "Abdomen & Pelvis", "Neuroanatomy",
            "Histology of Oral Tissues", "Embryology of Face & Teeth",
            "Medical Genetics", "Osteology & Forensic Identification",
        ),
        defaultQuery = "anatomical variation head neck morphometry cadaveric study dental",
        synonyms = listOf(
            "gross anatomy", "human anatomy", "morphometry",
            "anatomical variation", "foramen", "ossification",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-anat-morphometry",
                name = "Craniofacial morphometry",
                keywords = listOf("morphometry", "foramen", "mandible", "measurements"),
                exposures = listOf("dry skull side", "sex", "age group"),
                outcomes = listOf("linear distances", "foramen diameters", "angles"),
                methods = listOf("caliper measurement", "osteology recording sheet", "ImageJ on photographs"),
            ),
            ClusterSeed(
                id = "seed-anat-variations",
                name = "Anatomical variations",
                keywords = listOf("variation", "accessory", "anomaly", "incidence"),
                exposures = listOf("population side", "cadaver cohort"),
                outcomes = listOf("incidence percentages"),
                methods = listOf("dissection records review", "photographic documentation"),
            ),
        ),
        equipment = listOf(
            "sliding caliper", "digital vernier caliper", "measuring tape",
            "hand lens", "microscope slides", "articulated skull set",
        ),
        ethicsWatch = listOf(
            "cadaveric material handling per university norms",
            "written consent for clinical photographs",
            "no dissection beyond curriculum without IEC approval",
        ),
    ),

    Subject(
        id = "physiology",
        name = "Physiology",
        year = "first",
        blurb = "Systemic human physiology with practical emphasis on cardiorespiratory, hematological and nerve–muscle experiments.",
        domains = listOf(
            "Blood & Hematology", "Cardiovascular System", "Respiratory System",
            "Gastrointestinal & Salivary Glands", "Renal Physiology",
            "Endocrinology", "Nerve-Muscle Physiology", "CNS & Special Senses",
            "Reproductive Physiology", "Body Temperature & Skin",
        ),
        defaultQuery = "dental students blood pressure heart rate stress physiological measurement",
        synonyms = listOf(
            "human physiology", "cardiovascular", "spirometry",
            "reaction time", "vital signs",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-phys-cv",
                name = "Cardiovascular responses",
                keywords = listOf("blood pressure", "heart rate", "response", "exercise"),
                exposures = listOf("posture", "caffeine", "exam stress", "audio stimulation"),
                outcomes = listOf("SBP/DBP", "pulse rate", "SpO2"),
                methods = listOf("sphygmomanometer", "pulse oximeter", "standardized protocol sheet"),
            ),
            ClusterSeed(
                id = "seed-phys-resp",
                name = "Respiratory measures",
                keywords = listOf("peak flow", "spirometry", "respiratory"),
                exposures = listOf("anthropometry", "physical activity level"),
                outcomes = listOf("PEFR", "breath-holding time"),
                methods = listOf("peak flow meter", "triplicate recording"),
            ),
        ),
        equipment = listOf(
            "sphygmomanometer", "stethoscope", "pulse oximeter",
            "peak flow meter", "hand-grip dynamometer", "reaction-time board/app",
        ),
        ethicsWatch = listOf(
            "volunteer informed consent",
            "no invasive sampling in UG projects",
            "stop rules for dizziness or exertion discomfort",
        ),
    ),

    Subject(
        id = "biochemistry",
        name = "Biochemistry",
        year = "first",
        blurb = "Biomolecules, enzymes and metabolism with chairside-relevant applications such as salivary pH and buffering.",
        domains = listOf(
            "Carbohydrates & Energy Metabolism", "Lipids & Lipoproteins",
            "Proteins & Amino Acids", "Enzymes & Metabolic Regulation",
            "Vitamins & Deficiency", "Minerals & Calcium Metabolism",
            "Salivary Biochemistry (pH, Buffering, Amylase)",
            "Molecular Biology & Genetics", "Clinical Biochemistry (LFT, KFT, Glucose)",
            "Hemoglobin & Porphyria", "Immunoglobulins",
        ),
        defaultQuery = "saliva pH buffering capacity dental caries biochemical assay",
        synonyms = listOf(
            "salivary biochemistry", "pH", "buffering capacity",
            "biomarkers", "enzymes",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-bioch-ph",
                name = "Salivary pH & buffering",
                keywords = listOf("saliva", "pH", "buffering", "flow rate"),
                exposures = listOf("time of day", "sugary drink challenge", "fasting status", "oral hygiene"),
                outcomes = listOf("unstimulated salivary pH", "buffering score"),
                methods = listOf("pH strip or meter", "stimulated saliva collection"),
            ),
            ClusterSeed(
                id = "seed-bioch-enzymes",
                name = "Enzyme markers",
                keywords = listOf("enzyme", "amylase", "activity", "assay"),
                exposures = listOf("stress", "diet pattern", "smoking status"),
                outcomes = listOf("amylase activity", "colorimetric readings"),
                methods = listOf("colorimetric kit assay", "colorimeter/spectrophotometer"),
            ),
        ),
        equipment = listOf(
            "pH strips", "portable pH meter", "colorimeter",
            "centrifuge (shared facility)", "graduated pipettes", "water bath",
        ),
        ethicsWatch = listOf(
            "saliva collection informed consent",
            "no blood collection without IEC approval",
            "biosafety for sample handling and disposal",
        ),
    ),

    Subject(
        id = "dental-anatomy",
        name = "Dental Anatomy",
        year = "first",
        blurb = "Morphology, occlusion and identification of teeth; tooth carving and crown dimension recording.",
        domains = listOf(
            "Tooth Morphology (Permanent & Deciduous)", "Odontometry & Crown Dimensions",
            "Occlusion & jaw relations", "Tooth Identification & Notation Systems",
            "Oral Embryology (Face, Jaws, Palate, Teeth Development)",
            "Oral Histology (Enamel, Dentine, Cementum, PDL, Bone)",
            "Salivary Gland Histology", "TMJ Histology",
            "Oral Physiology (Saliva, Mastication, Deglutition, Taste, Speech)",
            "Calcium-Phosphorus-Fluoride Metabolism", "Calculus Formation",
        ),
        defaultQuery = "tooth morphology crown dimensions odontometry caries prevalence",
        synonyms = listOf(
            "tooth morphology", "odontometry", "crown dimensions",
            "mesiodistal", "buccolingual",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-dent-odontometry",
                name = "Odontometry",
                keywords = listOf("mesiodistal", "buccolingual", "dimensions", "odontometry"),
                exposures = listOf("tooth type", "sex", "arch side"),
                outcomes = listOf("crown widths", "coronal heights"),
                methods = listOf("digital caliper", "ImageJ photogrammetry"),
            ),
            ClusterSeed(
                id = "seed-dent-caries",
                name = "Early caries indicators",
                keywords = listOf("caries", "prevalence", "DMFT", "children"),
                exposures = listOf("diet", "fluoride exposure", "oral hygiene habits"),
                outcomes = listOf("DMFT/dmft index", "visible lesions"),
                methods = listOf("screening under torchlight", "WHO oral assessment form"),
            ),
        ),
        equipment = listOf(
            "digital vernier caliper", "extracted-tooth collection (departmental)",
            "ImageJ", "wax blocks and carving instruments", "millimeter graph sheets",
        ),
        ethicsWatch = listOf(
            "extracted-tooth storage and disposal per biomedical waste rules",
            "de-identification of casts and photographs",
        ),
    ),

    // ---------------- Second BDS ----------------

    Subject(
        id = "dental-materials",
        name = "Dental Materials",
        year = "second",
        blurb = "Physical, chemical and mechanical properties of restorative materials with simple laboratory testing options.",
        domains = listOf(
            "Gypsum Products", "Impression Materials (Hydrocolloid, Elastomers)",
            "Resin Composites & Bonding", "Glass Ionomer Cements",
            "Dental Amalgam", "Metals, Alloys & Casting",
            "Dental Ceramics & Porcelain", "Acrylic Resins & Denture Base",
            "Waxes & Investments", "Soldering & Welding",
            "Orthodontic Wires (NiTi, SS, Beta-Ti)",
            "Dental Implants (Biomaterials, Osseointegration)",
            "Biocompatibility & Testing", "Physical Properties & Adhesion",
        ),
        defaultQuery = "glass ionomer resin composite surface roughness flexural strength in vitro",
        synonyms = listOf(
            "biomaterials", "glass ionomer", "composite resin",
            "polymerization", "hardness",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-mat-mechanical",
                name = "Mechanical properties",
                keywords = listOf("flexural", "compressive", "strength", "hardness"),
                exposures = listOf("material brand", "filler load", "curing time"),
                outcomes = listOf("MPa values", "hardness numbers"),
                methods = listOf("UTM three-point bend test", "microhardness testing"),
            ),
            ClusterSeed(
                id = "seed-mat-surface",
                name = "Surface behavior",
                keywords = listOf("surface roughness", "wear", "polish", "staining"),
                exposures = listOf("polishing protocol", "beverage immersion", "brushing cycles"),
                outcomes = listOf("Ra roughness values", "color change ΔE"),
                methods = listOf("profilometer/ImageJ", "color measurement app", "immersion cycle protocol"),
            ),
        ),
        equipment = listOf(
            "universal testing machine (central facility)", "pH meter", "ImageJ",
            "articulator", "stone casts", "polishing kits",
        ),
        ethicsWatch = listOf(
            "material safety data sheet handling",
            "sharps disposal protocol",
            "no intraoral testing of new materials without IEC",
        ),
    ),

    Subject(
        id = "pathology",
        name = "General Pathology",
        year = "second",
        blurb = "Disease mechanisms, inflammation and neoplasia with hematology practicals and clinicopathologic correlation.",
        domains = listOf(
            "Cell Injury & Necrosis", "Inflammation & Healing",
            "Hematology (Anemia, Leukemia, Bleeding Disorders)",
            "Neoplasia & Carcinogenesis", "Immunopathology & Hypersensitivity",
            "Infections (TB, Syphilis, AIDS)", "Cardiovascular Pathology",
            "Nutritional Disorders", "Oral Cavity Diseases",
            "Salivary Gland Pathology", "Bone Diseases",
            "Cysts & Tumors of Jaws", "Mucocutaneous Lesions",
        ),
        defaultQuery = "anemia prevalence hemoglobin estimation dental outpatient patients",
        synonyms = listOf("hemoglobin", "anemia", "inflammation", "ESR", "blood film"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-path-anemia",
                name = "Anemia burden",
                keywords = listOf("anemia", "hemoglobin", "prevalence", "deficiency"),
                exposures = listOf("sex", "diet pattern", "age group"),
                outcomes = listOf("Hb g/dL", "clinical pallor signs"),
                methods = listOf("hemoglobinometer", "structured proforma"),
            ),
            ClusterSeed(
                id = "seed-path-inflammation",
                name = "Inflammatory markers",
                keywords = listOf("ESR", "CRP", "inflammation", "marker"),
                exposures = listOf("periodontal status", "systemic condition"),
                outcomes = listOf("ESR mm/hr", "CRP tiers"),
                methods = listOf("record-based review", "lab report extraction"),
            ),
        ),
        equipment = listOf(
            "hemoglobinometer (capillary)", "microscope", "staining reagents",
            "centrifuge (shared)", "ESR stand",
        ),
        ethicsWatch = listOf(
            "finger-prick sampling needs IEC plus trained personnel",
            "biohazard disposal protocol",
            "referral pathway for abnormal values",
        ),
    ),

    Subject(
        id = "microbiology",
        name = "Microbiology",
        year = "second",
        blurb = "Bacteriology, virology, mycology and infection control relevant to dental practice and salivary studies.",
        domains = listOf(
            "General Bacteriology & Sterilization",
            "Immunology & Antigens-Antibodies",
            "Cariogenic Streptococci & Dental Plaque Flora",
            "Anaerobic Bacteria (Periodontal Pathogens)",
            "Mycobacteria, Spirochaetes & Actinomycetes",
            "Virology (Herpes, Hepatitis B, HIV)",
            "Mycology (Candidiasis)",
            "Parasitology", "Infection Control in Dental Practice",
            "Antimicrobial Resistance",
        ),
        defaultQuery = "salivary Streptococcus mutans count caries children microbiology",
        synonyms = listOf("S. mutans", "Lactobacillus", "biofilm", "disinfection", "CFU"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-micro-smutans",
                name = "Caries-associated flora",
                keywords = listOf("Streptococcus mutans", "Lactobacillus", "CFU", "saliva"),
                exposures = listOf("caries status", "sugar frequency", "dentifrice type"),
                outcomes = listOf("CFU/mL counts"),
                methods = listOf("salivary sampling", "agar plating", "colony counting"),
            ),
            ClusterSeed(
                id = "seed-micro-disinfection",
                name = "Disinfection efficacy",
                keywords = listOf("disinfectant", "contact time", "efficacy", "surface"),
                exposures = listOf("agent concentration", "contact time"),
                outcomes = listOf("zone of inhibition", "CFU reduction"),
                methods = listOf("swab sampling pre/post", "agar well diffusion"),
            ),
        ),
        equipment = listOf(
            "inoculating loops", "Bunsen burner", "incubator (departmental)",
            "nutrient / Mitis salivarius agar", "autoclave (shared)", "sterile swab sticks",
        ),
        ethicsWatch = listOf(
            "culture safety and biosafety-level compliance",
            "infectious waste autoclaving before disposal",
            "participant anonymity in culture reports",
        ),
    ),

    Subject(
        id = "pharmacology",
        name = "Pharmacology",
        year = "second",
        blurb = "Drug actions, adverse effects and rational prescribing with emphasis on dental analgesics and antibiotics.",
        domains = listOf(
            "General Principles (Pharmacokinetics, Pharmacodynamics)",
            "CNS Drugs (Analgesics, Anesthetics, Anxiolytics)",
            "Autonomic Nervous System Drugs",
            "Antimicrobials & Chemotherapy",
            "Cardiovascular Drugs", "Autacoids & Anti-inflammatory",
            "Endocrine Drugs (Diabetes, Steroids)",
            "Dental Pharmacology (Topical Fluorides, Antiseptics, Obtundents)",
            "Prescribing Patterns & Drug Utilization",
            "Emergency Drugs in Dental Practice",
            "Local Anesthetics (Mechanism, Vasoconstrictors)",
        ),
        defaultQuery = "antibiotic prescribing pattern dentists survey amoxicillin awareness",
        synonyms = listOf(
            "prescribing patterns", "amoxicillin", "NSAID",
            "analgesic", "drug utilization",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-pharm-prescribing",
                name = "Prescribing patterns",
                keywords = listOf("antibiotic", "prescribing", "pattern", "dentists"),
                exposures = listOf("clinical scenario", "clinician seniority"),
                outcomes = listOf("drug choice", "dose", "duration"),
                methods = listOf("prescription audit", "structured questionnaire"),
            ),
            ClusterSeed(
                id = "seed-pharm-analgesics",
                name = "Analgesic use",
                keywords = listOf("NSAID", "ibuprofen", "analgesic", "self-medication"),
                exposures = listOf("pain severity", "advice source"),
                outcomes = listOf("usage frequency", "recalled adverse effects"),
                methods = listOf("interview schedule", "pharmacy counter survey"),
            ),
        ),
        equipment = listOf(
            "structured questionnaire forms", "pharmacy OP records access",
            "prescription audit sheets", "spreadsheet for analysis",
        ),
        ethicsWatch = listOf(
            "prescription data anonymization",
            "no intervention on ongoing therapy",
            "permission from records section",
        ),
    ),

    // ---------------- Third BDS ----------------

    Subject(
        id = "oral-pathology",
        name = "Oral Pathology",
        year = "third",
        blurb = "Oral diseases — potentially malignant disorders, mucosal lesions and tumors — with screening and cytology options.",
        domains = listOf(
            "Developmental Disturbances of Teeth & Jaws",
            "Dental Caries (Etiopathogenesis, Microbiology, Prevention)",
            "Pulp & Periapical Pathology", "Periodontal Disease Pathology",
            "Oral Mucosal Diseases (Lichen Planus, Pemphigus, Lupus)",
            "Oral Potentially Malignant Disorders (Leukoplakia, OSMF)",
            "Oral Cancer (TNM, Biopsy, Cytology)",
            "Odontogenic Tumors (Ameloblastoma, Myxoma)",
            "Salivary Gland Tumors", "Cysts of Jaws",
            "Bone Diseases (Fibrous Dysplasia, Paget's, Osteomyelitis)",
            "Forensic Odontology",
        ),
        defaultQuery = "oral potentially malignant disorder leukoplakia OSMF prevalence screening India",
        synonyms = listOf(
            "leukoplakia", "OSMF", "erythroplakia",
            "toluidine blue", "exfoliative cytology",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-opath-osmf",
                name = "OSMF & areca nut",
                keywords = listOf("oral submucous fibrosis", "areca nut", "gutkha", "prevalence"),
                exposures = listOf("areca/gutkha use", "duration", "frequency"),
                outcomes = listOf("OSMF grade", "mouth opening"),
                methods = listOf("screening camp examination", "interincisal distance measurement"),
            ),
            ClusterSeed(
                id = "seed-opath-screening",
                name = "Chairside screening aids",
                keywords = listOf("toluidine blue", "screening", "detection", "sensitivity"),
                exposures = listOf("stain versus naked-eye exam"),
                outcomes = listOf("detection yield", "false positives"),
                methods = listOf("parallel screening", "photographic documentation"),
            ),
        ),
        equipment = listOf(
            "mouth mirrors", "LED torch", "disposable gloves",
            "toluidine blue kit (if sanctioned)", "intraoral camera or phone adapter",
        ),
        ethicsWatch = listOf(
            "mandatory referral pathway for suspicious lesions",
            "biopsy informed consent",
            "photographic confidentiality",
        ),
    ),

    Subject(
        id = "oral-medicine",
        name = "Oral Medicine",
        year = "third",
        blurb = "Diagnosis and non-surgical management of oral conditions, orofacial pain and medically compromised patients.",
        domains = listOf(
            "Clinical Diagnosis & Examination",
            "Orofacial Pain (Trigeminal Neuralgia, Referred Pain)",
            "TMJ Disorders & MPDS", "Oral Ulceration & Vesiculobullous Lesions",
            "Salivary Gland Disorders (Sialometry, Sialadenitis)",
            "Xerostomia & Burning Mouth Syndrome",
            "Oral Manifestations of Systemic Disease",
            "Medically Compromised Patient Management",
            "Behavioral Sciences & Psychology",
            "Forensic Odontology (Dental Records, Bite Marks)",
            "Radiotherapy Complications in Oral Cavity",
        ),
        defaultQuery = "temporomandibular disorder prevalence mouth opening dental patients",
        synonyms = listOf("TMD", "temporomandibular", "burning mouth", "xerostomia", "halitosis"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-omed-tmd",
                name = "TMD signs & symptoms",
                keywords = listOf("temporomandibular", "clicking", "deviation", "mouth opening"),
                exposures = listOf("bruxism traits", "stress scores", "posture/screen habits"),
                outcomes = listOf("maximum mouth opening", "VAS pain", "Helkimo index"),
                methods = listOf("ruler measurement", "DC/TMD questionnaire"),
            ),
            ClusterSeed(
                id = "seed-omed-xerostomia",
                name = "Xerostomia & systemic links",
                keywords = listOf("xerostomia", "dry mouth", "medication", "diabetes"),
                exposures = listOf("medication list", "systemic status"),
                outcomes = listOf("sialometry values", "questionnaire scores"),
                methods = listOf("timed sialometry", "symptom questionnaires"),
            ),
        ),
        equipment = listOf(
            "plastic ruler/caliper for mouth opening", "joint-sound stethoscope (optional)",
            "validated questionnaires (DC/TMD printouts)", "VAS scales",
        ),
        ethicsWatch = listOf(
            "questionnaire informed consent",
            "referral for systemic red flags detected during examination",
        ),
    ),

    Subject(
        id = "oral-radiology",
        name = "Oral Radiology",
        year = "third",
        blurb = "Radiographic technique and interpretation using EXISTING images — no new exposures for student projects.",
        domains = listOf(
            "Radiation Physics & Safety",
            "Intraoral Radiography (Periapical, Bite-wing, Occlusal)",
            "Extraoral Radiography (OPG, Cephalogram, TMJ)",
            "CBCT Basics & Interpretation",
            "Radiographic Anatomy & Landmarks",
            "Radiographic Pathology Detection",
            "Radiographic Morphometry & Measurements",
            "Incidental Findings on Panoramic Radiographs",
            "Contrast Radiography & Sialography",
            "Radiotherapy Effects on Oral Structures",
        ),
        defaultQuery = "panoramic radiograph incidental findings prevalence retrospective dental",
        synonyms = listOf("OPG", "panoramic", "periapical", "CBCT", "radiographic assessment"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-orad-opg-findings",
                name = "OPG incidental findings",
                keywords = listOf("panoramic", "incidental", "findings", "prevalence"),
                exposures = listOf("age band", "sex"),
                outcomes = listOf("finding frequencies: calcifications, impacted teeth, bone patterns"),
                methods = listOf("retrospective archive review", "dual-examiner reading"),
            ),
            ClusterSeed(
                id = "seed-orad-morphometry",
                name = "Radiographic morphometry",
                keywords = listOf("radiographic", "measurement", "mandibular", "cortical"),
                exposures = listOf("age", "sex"),
                outcomes = listOf("cortical width", "condylar dimensions"),
                methods = listOf("ImageJ calibration on archived images"),
            ),
        ),
        equipment = listOf(
            "existing OPG/IOPA archives", "viewer/lightbox",
            "ImageJ for linear measurements", "anonymized reporting sheets",
        ),
        ethicsWatch = listOf(
            "NO NEW RADIATION for UG projects — archival/retrospective only",
            "image anonymization before analysis",
            "archive custodian permission",
        ),
    ),

    Subject(
        id = "periodontology",
        name = "Periodontology",
        year = "third",
        blurb = "Periodontal health, disease classification and prevention with clinical indices ideal for undergraduate studies.",
        domains = listOf(
            "Periodontal Anatomy (Gingiva, PDL, Cementum, Alveolar Bone)",
            "Plaque Biofilm & Calculus", "Gingival Diseases Classification",
            "Periodontitis Classification & Staging",
            "Host Response & Immunopathology",
            "Risk Factors (Smoking, Diabetes, Stress)",
            "Clinical Indices (GI, PI, CPITN, CPI, CAL)",
            "Non-Surgical Therapy (SRP, Curettage)",
            "Surgical Therapy (Flaps, Osseous, GTR)",
            "Mucogingival & Plastic Surgery",
            "Periodontal-Systemic Links (Cardiovascular, Preterm Birth)",
            "Implants & Peri-implantitis",
            "Supportive Periodontal Therapy",
        ),
        defaultQuery = "gingival index plaque periodontal status prevalence dental students",
        synonyms = listOf("gingival index", "probing depth", "CAL", "CPI", "plaque index"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-perio-indices",
                name = "Index-based status surveys",
                keywords = listOf("gingival index", "plaque index", "CPI", "prevalence"),
                exposures = listOf("oral hygiene habits", "smoking", "education level"),
                outcomes = listOf("GI/PI scores", "CPI codes"),
                methods = listOf("standard index examination", "single calibrated examiner"),
            ),
            ClusterSeed(
                id = "seed-perio-systemic",
                name = "Periodontal–systemic links",
                keywords = listOf("periodontitis", "diabetes", "association", "glycemic"),
                exposures = listOf("glycemic status (self-reported or archival)"),
                outcomes = listOf("probing depth", "clinical attachment loss"),
                methods = listOf("cross-sectional clinical examination", "record linkage"),
            ),
        ),
        equipment = listOf(
            "Williams/CPI periodontal probes", "mouth mirrors",
            "plaque disclosing tablets", "sterile gauze", "recording proformas",
        ),
        ethicsWatch = listOf(
            "minimize probing discomfort",
            "cross-infection control between participants",
            "consent for clinical examination",
        ),
    ),

    Subject(
        id = "endodontics",
        name = "Endodontics",
        year = "third",
        blurb = "Pulp biology, root canal principles and outcomes — often best studied via clinical records and pain scales.",
        domains = listOf(
            "Pulp Biology & Diagnosis", "Vital Pulp Therapy (Capping, Pulpotomy)",
            "Root Canal Treatment Principles", "Access Cavity Preparation",
            "Working Length Determination", "Cleaning & Shaping Techniques",
            "Root Canal Irrigants & Disinfection", "Obturation Methods",
            "Post-Endodontic Restoration", "Endodontic Surgery (Apicectomy)",
            "Traumatized Teeth Management", "Revascularization & Apexification",
            "Nickel-Titanium Instruments", "Lasers in Endodontics",
            "Post-Operative Pain Assessment",
        ),
        defaultQuery = "root canal treatment post-operative pain visual analogue scale working length",
        synonyms = listOf(
            "RCT", "apex locator", "VAS pain",
            "rotary instrumentation", "post-operative pain",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-endo-pain",
                name = "Post-operative pain",
                keywords = listOf("post-operative pain", "VAS", "root canal", "24 hours"),
                exposures = listOf("technique", "intracanal medicament", "pre-op pulpal status"),
                outcomes = listOf("VAS at 24/48 h", "analgesic intake"),
                methods = listOf("phone follow-up sheet", "clinical records review"),
            ),
            ClusterSeed(
                id = "seed-endo-length",
                name = "Working length accuracy",
                keywords = listOf("apex locator", "working length", "accuracy", "radiograph"),
                exposures = listOf("device generation", "canal type"),
                outcomes = listOf("±0.5 mm agreement rates"),
                methods = listOf("archival comparison: apex locator vs radiograph"),
            ),
        ),
        equipment = listOf(
            "electronic apex locator (clinical)", "VAS numeric rating cards",
            "archived RCT records", "existing periapical radiographs",
        ),
        ethicsWatch = listOf(
            "all care delivered under faculty supervision",
            "record-use permission from the department",
            "patient identity protection",
        ),
    ),

    // ---------------- Final BDS ----------------

    Subject(
        id = "omfs",
        name = "Oral & Maxillofacial Surgery",
        year = "final",
        blurb = "Exodontia, minor oral surgery and hospital-based protocols — commonly studied through outcome records.",
        domains = listOf(
            "Exodontia & Surgical Techniques",
            "Impacted Teeth (Classification, Surgical Removal)",
            "Pre-Prosthetic Surgery", "Maxillary Sinus Diseases",
            "TMJ Disorders (Dislocation, Ankylosis, Internal Derangement)",
            "Odontogenic Infections & Fascial Spaces",
            "Fractures of Jaws & Fixation",
            "Salivary Gland Diseases (Sialolithiasis, Tumors)",
            "Cleft Lip & Palate", "Jaw Deformities",
            "Oral Implantology (Osseointegration, Staged Surgery)",
            "Local Anesthesia (Nerve Blocks)",
            "General Anesthesia & IV Sedation",
            "Medical Emergencies in Dental Practice",
        ),
        defaultQuery = "third molar extraction postoperative swelling trismus pain outcome",
        synonyms = listOf("wisdom tooth", "dry socket", "alveolar osteitis", "trismus", "swelling"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-omfs-third-molar",
                name = "Third molar outcomes",
                keywords = listOf("third molar", "extraction", "swelling", "trismus"),
                exposures = listOf("impaction type", "surgical technique", "age"),
                outcomes = listOf("facial swelling mm", "mouth opening", "VAS", "dry socket rate"),
                methods = listOf("day 2/7 follow-up measurements", "OT/OPD register review"),
            ),
            ClusterSeed(
                id = "seed-omfs-emergencies",
                name = "Medical emergencies readiness",
                keywords = listOf("syncope", "emergency", "drugs", "preparedness"),
                exposures = listOf("clinic type", "staff training"),
                outcomes = listOf("emergency kit completeness score", "drill knowledge score"),
                methods = listOf("checklist audit", "staff questionnaire"),
            ),
        ),
        equipment = listOf(
            "measuring tape/digital caliper for facial swelling",
            "maximum-interincisal ruler (trismus)", "VAS cards", "OT/OPD registers",
        ),
        ethicsWatch = listOf(
            "surgical consent processes respected",
            "register data anonymization",
            "no alteration of clinical care for study purposes",
        ),
    ),

    Subject(
        id = "pedodontics",
        name = "Pediatric & Preventive Dentistry",
        year = "final",
        blurb = "Children's oral health, behavior guidance and prevention — strong fit for school-based surveys.",
        domains = listOf(
            "Growth & Development (Craniofacial, Dental Arch)",
            "Development of Occlusion", "Dental Caries in Children (ECC, Rampant)",
            "Pediatric Operative Dentistry (SS Crowns, Strip Crowns)",
            "Pediatric Endodontics (Pulpotomy, Pulpectomy)",
            "Traumatic Injuries in Children",
            "Behavior Management (Frankl, Tell-Show-Do)",
            "Preventive & Interceptive Orthodontics",
            "Oral Habits (Thumb Sucking, Tongue Thrust)",
            "Dental Care of Special Needs Children",
            "Fluoride Programs & Sealants",
            "Diet & Nutrition Counseling",
            "School Dental Health Programs",
        ),
        defaultQuery = "early childhood caries prevalence preschool children dmft risk factors",
        synonyms = listOf("ECC", "dmft", "sealants", "fluoride varnish", "behavior management"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-pedo-ecc",
                name = "ECC epidemiology",
                keywords = listOf("early childhood caries", "prevalence", "risk factors", "preschool"),
                exposures = listOf("feeding history", "snacking frequency", "caregiver education"),
                outcomes = listOf("dmft/deft indices"),
                methods = listOf("school screening", "parent interview form"),
            ),
            ClusterSeed(
                id = "seed-pedo-behavior",
                name = "Behavior guidance",
                keywords = listOf("behavior", "anxiety", "Frankl", "management"),
                exposures = listOf("age group", "first visit vs recall visit"),
                outcomes = listOf("Frankl rating", "treatment completion rates"),
                methods = listOf("chairside observation rating"),
            ),
        ),
        equipment = listOf(
            "dmft/DMFT proformas", "mouth mirrors",
            "screening probes", "LED torches", "portable chairs",
        ),
        ethicsWatch = listOf(
            "parental consent AND child assent required",
            "school authority permission",
            "safeguarding during examination",
        ),
    ),

    Subject(
        id = "orthodontics",
        name = "Orthodontics",
        year = "final",
        blurb = "Malocclusion assessment and appliance effects using indexes and existing study models.",
        domains = listOf(
            "Growth & Development Theories",
            "Classification of Malocclusion (Angle's, Ackerman-Proffit)",
            "Cephalometric Analysis (Steiner's, Downs', Tweed's, Ricketts')",
            "Diagnosis & Treatment Planning",
            "Biomechanics of Tooth Movement",
            "Preventive & Interceptive Orthodontics",
            "Removable Appliances (Expansion, Hawley)",
            "Fixed Appliances (Edgewise, Begg's, Straight-Wire)",
            "Extra-Oral Appliances (Headgear, Chin Cup)",
            "Myofunctional Appliances (Activator, Twin Block, Frankel)",
            "Orthodontic Management of Cleft Lip & Palate",
            "Retention & Relapse",
            "Oral Hygiene with Appliances",
        ),
        defaultQuery = "malocclusion prevalence DAI IOTN adolescents orthodontic treatment need",
        synonyms = listOf("DAI", "IOTN", "overjet", "crowding", "study models"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-ortho-prevalence",
                name = "Treatment need epidemiology",
                keywords = listOf("malocclusion", "prevalence", "DAI", "IOTN", "adolescents"),
                exposures = listOf("age band", "sex", "urban/rural school"),
                outcomes = listOf("DAI scores", "treatment-need categories"),
                methods = listOf("school screening with index rulers"),
            ),
            ClusterSeed(
                id = "seed-ortho-hygiene",
                name = "Appliance & hygiene effects",
                keywords = listOf("fixed appliance", "plaque", "gingivitis", "oral hygiene"),
                exposures = listOf("appliance presence", "hygiene instruction"),
                outcomes = listOf("PI/GI scores around brackets"),
                methods = listOf("indexed examination", "disclosing agent"),
            ),
        ),
        equipment = listOf(
            "DAI/IOTN rulers and charts", "sliding calipers",
            "existing plaster study models", "ImageJ on standardized photos",
        ),
        ethicsWatch = listOf(
            "cast and photo de-identification",
            "no elective appliance changes for study purposes",
        ),
    ),

    Subject(
        id = "public-health",
        name = "Public Health / Community Dentistry",
        year = "final",
        blurb = "Population oral health, health education and program evaluation — camps, schools and communities.",
        domains = listOf(
            "Epidemiology & Biostatistics",
            "Oral Health Survey Methods (WHO)", "Disease Indices",
            "KAP Studies & Health Education",
            "Community Oral Health Programs",
            "Water Fluoridation & Defluoridation",
            "Tobacco Control & Cessation Programs",
            "School Dental Health Programs",
            "Research Methodology & Protocol Design",
            "Practice Management & Ethics",
            "Tele-dentistry & Digital Health",
            "Preventive Dentistry Levels",
        ),
        defaultQuery = "oral health knowledge attitude practice survey tobacco community students",
        synonyms = listOf("KAP study", "health education", "fluoridation", "tobacco cessation", "camp"),
        clusters = listOf(
            ClusterSeed(
                id = "seed-ph-kap",
                name = "Knowledge–attitude–practice",
                keywords = listOf("knowledge", "attitude", "practice", "oral health", "survey"),
                exposures = listOf("education level", "residence", "income proxy"),
                outcomes = listOf("KAP domain scores"),
                methods = listOf("validated questionnaire", "scoring rubric"),
            ),
            ClusterSeed(
                id = "seed-ph-tobacco",
                name = "Tobacco control",
                keywords = listOf("tobacco", "gutkha", "cessation", "awareness", "youth"),
                exposures = listOf("peer influence", "advertising exposure"),
                outcomes = listOf("use prevalence", "quit intention"),
                methods = listOf("anonymous school survey", "GYTS-adapted tool"),
            ),
        ),
        equipment = listOf(
            "WHO oral health assessment forms (modified)", "KAP questionnaires",
            "camp kits (mirrors, probes, torches)", "tally sheets or tablets",
        ),
        ethicsWatch = listOf(
            "institutional and school permissions",
            "anonymized aggregate reporting only",
            "sensitive-question handling (tobacco)",
        ),
    ),

    Subject(
        id = "interdisciplinary",
        name = "Interdisciplinary Engineering",
        year = "final",
        blurb = "Dental–engineering blend: 3D printing, CAD/CAM, sensors and imaging analytics using college facilities.",
        domains = listOf(
            "3D Printing", "CAD/CAM", "Sensors & IoT",
            "Imaging Analytics", "Biomaterials Engineering", "Instrumentation",
        ),
        defaultQuery = "3D printed dental model dimensional accuracy CAD CAM engineering",
        synonyms = listOf(
            "additive manufacturing", "stereolithography", "ImageJ analysis",
            "sensor prototype", "dimensional accuracy",
        ),
        clusters = listOf(
            ClusterSeed(
                id = "seed-inter-printing",
                name = "Printed model accuracy",
                keywords = listOf("3D printing", "accuracy", "dimensional", "dental model"),
                exposures = listOf("printer type", "layer height", "resin brand"),
                outcomes = listOf("deviation in mm vs master model"),
                methods = listOf("caliper/ImageJ comparison", "repeated prints"),
            ),
            ClusterSeed(
                id = "seed-inter-sensors",
                name = "Chairside sensing",
                keywords = listOf("sensor", "pH", "prototype", "monitoring"),
                exposures = listOf("beverage challenge", "brushing event"),
                outcomes = listOf("signal curves", "repeatability"),
                methods = listOf("bench-top prototype logging", "calibration curve"),
            ),
        ),
        equipment = listOf(
            "departmental/engineering-dept 3D printer and scanner access",
            "ImageJ/Fiji", "digital caliper for verification",
            "low-cost microcontroller kits (via engineering dept)",
        ),
        ethicsWatch = listOf(
            "device safety review before any patient contact",
            "inter-departmental collaboration approval",
            "no clinical claims from bench work",
        ),
    ),
)

fun subjectsForYear(yearId: String): List<Subject> =
    SUBJECTS.filter { it.year == yearId }

fun getYear(id: String): Year? =
    YEARS.firstOrNull { it.id == id }

fun getSubject(yearId: String, subjectId: String): Subject? =
    SUBJECTS.firstOrNull { it.year == yearId && it.id == subjectId }
