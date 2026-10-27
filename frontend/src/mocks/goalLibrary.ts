import type { InterventionGoal } from "../types";

// Original illustrative content for the UI demo, not a standardized assessment bank.
export const goalAges = [
  { id: "9-18", label: "9–18 tháng tuổi" },
  { id: "18-24", label: "18–24 tháng tuổi" },
  { id: "24-36", label: "24–36 tháng tuổi" },
  { id: "36-48", label: "36–48 tháng tuổi" },
];
export const goalDomains = [
  {
    id: "imitation",
    name: "Bắt chước",
    description: "Học qua quan sát và làm theo",
    code: "4",
    group: "Bắt chước hành động trong hoạt động chơi",
    samples: [
      [
        "Bắt chước vỗ tay khi người lớn làm mẫu.",
        "Bắt chước gõ hai đồ vật vào nhau trong khi chơi.",
      ],
      [
        "Bắt chước hai hành động liên tiếp với đồ chơi khi được làm mẫu.",
        "Bắt chước cho búp bê ăn hoặc uống trong trò chơi giả vờ.",
      ],
      [
        "Bắt chước chuỗi ba hành động có liên quan với đồ vật.",
        "Bắt chước các động tác tay trong một bài hát quen thuộc.",
      ],
      [
        "Bắt chước chuỗi hành động chơi giả vờ cùng một người bạn.",
        "Quan sát và làm lại cách lắp một mô hình đơn giản.",
      ],
    ],
  },
  {
    id: "cognition",
    name: "Nhận thức",
    description: "Khám phá, phân loại và giải quyết vấn đề",
    code: "5",
    group: "Nhận biết và phân loại đồ vật",
    samples: [
      [
        "Tìm đồ chơi được giấu một phần dưới khăn.",
        "Ghép hai đồ vật giống nhau khi được gợi ý.",
      ],
      [
        "Ghép đồ vật với hình ảnh tương ứng.",
        "Phân loại hai nhóm đồ chơi quen thuộc.",
      ],
      [
        "Phân loại đồ vật theo màu sắc trong hoạt động chơi.",
        "Ghép các mảnh để hoàn thành một bức tranh đơn giản.",
      ],
      [
        "Phân loại đồ vật theo hai đặc điểm được hướng dẫn.",
        "Sắp xếp một chuỗi tranh sinh hoạt theo trình tự.",
      ],
    ],
  },
  {
    id: "fine-motor",
    name: "Vận động tinh",
    description: "Phối hợp bàn tay và ánh mắt",
    code: "7",
    group: "Phối hợp tay và mắt với đồ dùng học tập",
    samples: [
      [
        "Thả khối đồ chơi lớn vào hộp theo mẫu.",
        "Dùng ngón tay trỏ chạm vào hình ảnh được chỉ dẫn.",
      ],
      [
        "Xếp chồng các khối đồ chơi trong hoạt động cùng cô.",
        "Lật từng trang sách tranh với sự hỗ trợ khi cần.",
      ],
      [
        "Xâu các hạt đồ chơi lớn theo mẫu với người lớn giám sát.",
        "Vẽ các nét thẳng theo mẫu trên giấy.",
      ],
      [
        "Tô màu trong một hình có đường viền rõ.",
        "Dùng kẹp để chuyển các vật liệu học tập giữa hai khay.",
      ],
    ],
  },
  {
    id: "behavior",
    name: "Hành vi",
    description: "Tham gia hoạt động và thích nghi thay đổi",
    code: "10",
    group: "Tham gia nề nếp trong hoạt động hằng ngày",
    samples: [
      [
        "Cùng người lớn tham gia một hoạt động chơi ngắn.",
        "Chuyển chú ý sang đồ chơi được người lớn giới thiệu.",
      ],
      [
        "Chờ lượt ngắn khi chơi với người lớn.",
        "Chuyển sang hoạt động tiếp theo khi có tín hiệu quen thuộc.",
      ],
      [
        "Dùng cử chỉ hoặc lời nói để đề nghị nghỉ.",
        "Thực hiện nề nếp cất đồ chơi khi kết thúc hoạt động.",
      ],
      [
        "Lựa chọn cách đề nghị giúp đỡ khi gặp khó khăn.",
        "Theo dõi lịch hoạt động bằng tranh cùng giáo viên.",
      ],
    ],
  },
  {
    id: "independence",
    name: "Tự lập",
    description: "Từng bước chủ động trong sinh hoạt",
    code: "9",
    group: "Thực hiện các kỹ năng tự phục vụ",
    samples: [
      [
        "Đưa tay hợp tác khi được hỗ trợ mặc áo.",
        "Cầm cốc có quai với sự hỗ trợ của người lớn.",
      ],
      [
        "Cất đồ dùng cá nhân vào vị trí được hướng dẫn.",
        "Tham gia rửa tay cùng người lớn theo từng bước.",
      ],
      [
        "Tự dùng thìa trong bữa ăn với hỗ trợ khi cần.",
        "Thực hiện các bước rửa tay theo tranh minh họa.",
      ],
      [
        "Tự chuẩn bị đồ dùng cho một hoạt động quen thuộc.",
        "Mặc hoặc cởi áo khoác với hỗ trợ khi cần.",
      ],
    ],
  },
  {
    id: "social",
    name: "Kỹ năng xã hội",
    description: "Kết nối và tương tác với người khác",
    code: "3",
    group: "Chia sẻ và luân phiên trong tương tác",
    samples: [
      [
        "Tham gia trò chơi ú òa cùng người lớn.",
        "Chia sẻ sự chú ý vào đồ chơi qua cử chỉ hoặc ánh nhìn.",
      ],
      [
        "Đưa đồ vật cho người lớn để tiếp tục trò chơi.",
        "Luân phiên lăn bóng cùng người lớn.",
      ],
      [
        "Chủ động mời bạn cùng tham gia một trò chơi.",
        "Thay phiên sử dụng đồ chơi khi được nhắc.",
      ],
      [
        "Hợp tác với bạn để hoàn thành một hoạt động chơi.",
        "Chào hỏi và đáp lại bạn trong tình huống quen thuộc.",
      ],
    ],
  },
  {
    id: "receptive",
    name: "Ngôn ngữ tiếp nhận",
    description: "Lắng nghe và hiểu thông điệp",
    code: "1",
    group: "Hiểu lời nói và chỉ dẫn trong sinh hoạt",
    samples: [
      [
        "Quay đầu về phía âm thanh từ đồ chơi hoặc đồ vật.",
        "Phản ứng khi được gọi tên trong hoạt động quen thuộc.",
      ],
      [
        "Lấy một đồ vật quen thuộc theo yêu cầu bằng lời.",
        "Chỉ vào hình ảnh được gọi tên trong sách tranh.",
      ],
      [
        "Làm theo chỉ dẫn gồm hai hành động có liên quan.",
        "Chọn đồ vật theo công dụng khi được hỏi.",
      ],
      [
        "Hiểu các từ chỉ vị trí trong hoạt động với đồ chơi.",
        "Trả lời câu hỏi về một câu chuyện tranh ngắn.",
      ],
    ],
  },
  {
    id: "expressive",
    name: "Ngôn ngữ diễn đạt",
    description: "Bày tỏ mong muốn và ý tưởng",
    code: "2",
    group: "Sử dụng phương tiện giao tiếp để bày tỏ",
    samples: [
      [
        "Dùng cử chỉ để đề nghị tiếp tục một hoạt động thích thú.",
        "Phát âm hoặc dùng tín hiệu để thu hút sự chú ý.",
      ],
      [
        "Dùng từ hoặc tranh để yêu cầu một đồ vật quen thuộc.",
        "Gọi tên người hoặc đồ vật trong hoạt động chơi.",
      ],
      [
        "Kết hợp từ hoặc ký hiệu để bày tỏ mong muốn.",
        "Trả lời câu hỏi đơn giản về hoạt động đang diễn ra.",
      ],
      [
        "Kể lại một việc vừa làm bằng phương tiện giao tiếp phù hợp.",
        "Chủ động đặt câu hỏi để tìm hiểu đồ vật hoặc hoạt động.",
      ],
    ],
  },
  {
    id: "play",
    name: "Kỹ năng chơi",
    description: "Khám phá đồ chơi và mở rộng tưởng tượng",
    code: "6",
    group: "Mở rộng hoạt động chơi có ý nghĩa",
    samples: [
      [
        "Khám phá cách sử dụng một đồ chơi tạo âm thanh.",
        "Cùng người lớn lấy đồ vật ra và cất vào hộp.",
      ],
      [
        "Sử dụng đồ chơi đúng công dụng trong hoạt động quen thuộc.",
        "Thực hiện một hành động chơi giả vờ theo gợi ý.",
      ],
      [
        "Kết hợp các đồ chơi để xây dựng một tình huống chơi.",
        "Duy trì một hoạt động chơi với người lớn qua nhiều lượt.",
      ],
      [
        "Tạo chuỗi chơi giả vờ có mở đầu và kết thúc.",
        "Cùng bạn phân vai trong một trò chơi quen thuộc.",
      ],
    ],
  },
  {
    id: "gross-motor",
    name: "Vận động thô",
    description: "Phối hợp cơ thể qua trò chơi vận động",
    code: "8",
    group: "Tham gia các hoạt động vận động",
    samples: [
      [
        "Với tay lấy đồ chơi ở các vị trí phù hợp khi được hỗ trợ.",
        "Lăn bóng về phía người lớn trong hoạt động chơi.",
      ],
      [
        "Đi theo một lối ngắn được đánh dấu cùng người lớn.",
        "Đá quả bóng đặt phía trước trong trò chơi.",
      ],
      [
        "Bước qua các vật cản thấp trong đường chơi được giám sát.",
        "Ném bóng về phía một đích lớn.",
      ],
      [
        "Phối hợp các động tác trong một trò chơi vận động nhóm.",
        "Giữ thăng bằng theo một đường đi được đánh dấu.",
      ],
    ],
  },
];
export const goalPrograms = [
  {
    id: "denver",
    name: "CT can thiệp sớm Denver",
    shortName: "Denver",
    prefix: "ES",
    description: "Các lĩnh vực giao tiếp, tương tác, vận động và sinh hoạt.",
    domainIds: goalDomains.map((d) => d.id),
    color: "blue",
  },
  {
    id: "vb",
    name: "[VB] Ngôn ngữ và kỹ năng xã hội",
    shortName: "VB",
    prefix: "VB",
    description: "Chú trọng giao tiếp và tương tác trong hoạt động hằng ngày.",
    domainIds: ["receptive", "expressive", "social", "imitation", "play"],
    color: "teal",
  },
  {
    id: "preschool",
    name: "Chương trình giáo dục mầm non",
    shortName: "Mầm non",
    prefix: "MN",
    description: "Kỹ năng học tập, vui chơi và tự phục vụ.",
    domainIds: [
      "cognition",
      "fine-motor",
      "gross-motor",
      "social",
      "independence",
      "play",
    ],
    color: "amber",
  },
  {
    id: "small-steps",
    name: "[TBN] Từng Bước Nhỏ",
    shortName: "Từng Bước Nhỏ",
    prefix: "TBN",
    description: "Chia nhỏ kỹ năng để theo dõi từng bước phát triển.",
    domainIds: [
      "imitation",
      "cognition",
      "fine-motor",
      "gross-motor",
      "independence",
      "receptive",
    ],
    color: "violet",
  },
  {
    id: "ab",
    name: "[AB] Kỹ năng ngôn ngữ và học tập cơ bản",
    shortName: "AB",
    prefix: "AB",
    description: "Giao tiếp, nhận thức và tham gia hoạt động học tập.",
    domainIds: [
      "receptive",
      "expressive",
      "cognition",
      "social",
      "imitation",
      "behavior",
    ],
    color: "blue",
  },
];
export const goalCatalog: InterventionGoal[] = goalPrograms.flatMap((program) =>
  goalDomains
    .filter((domain) => program.domainIds.includes(domain.id))
    .flatMap((domain) =>
      goalAges.flatMap((age, level) =>
        domain.samples[level].map((description, i) => ({
          id: `${program.id}:${domain.id}:${age.id}:${i + 1}`,
          programId: program.id,
          programName: program.name,
          domainId: domain.id,
          domainName: domain.name,
          ageId: age.id,
          ageLabel: age.label,
          code: `${program.prefix}.${domain.code}.LV${level + 1}.1${String.fromCharCode(65 + i)}`,
          group: domain.group,
          description,
        })),
      ),
    ),
);
