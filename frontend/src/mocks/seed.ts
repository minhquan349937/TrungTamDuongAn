import type { Database, StudyType } from "../types";
import { today } from "../utils/format";
export function createSeed(): Database {
  const now = today(),
    month = now.slice(0, 7),
    year = Number(now.slice(0, 4));
  const names = [
    "Nguyễn Minh An",
    "Trần Gia Bảo",
    "Lê Khánh Linh",
    "Phạm Minh Khang",
    "Hoàng Ngọc Anh",
    "Vũ Hải Đăng",
    "Đặng Tuệ Nhi",
    "Bùi Nhật Minh",
    "Đỗ Bảo Châu",
    "Ngô Đức Anh",
    "Nguyễn Hà My",
    "Trần Thiên Phúc",
    "Lê Bảo Ngọc",
    "Phạm Hoàng Nam",
    "Hoàng Minh Thư",
    "Vũ Gia Hân",
    "Đặng Anh Khoa",
    "Bùi Phương Anh",
    "Đỗ Khánh An",
    "Ngô Nhật Hạ",
    "Nguyễn Đức Minh",
    "Trần Bảo An",
    "Lê Tuấn Kiệt",
    "Phạm An Nhiên",
    "Hoàng Gia Huy",
    "Vũ Minh Châu",
    "Đặng Quốc Bảo",
    "Bùi Mai Chi",
    "Đỗ Thiên An",
    "Ngô Khánh Vy",
  ];
  const staffNames = [
    "Nguyễn Thu Hà",
    "Trần Minh Ngọc",
    "Lê Hoàng Anh",
    "Phạm Thảo Linh",
    "Vũ Thanh Tâm",
    "Đặng Ngọc Mai",
    "Bùi Minh Trang",
    "Đỗ Phương Thảo",
    "Ngô Hải Yến",
    "Hoàng Kim Anh",
  ];
  const staff: Database["staff"] = staffNames.map((name, i) => ({
    id: "NV" + String(i + 1).padStart(3, "0"),
    name,
    specialty: [
      "Giáo dục đặc biệt",
      "Âm ngữ trị liệu",
      "Tâm lý giáo dục",
      "Hoạt động trị liệu",
    ][i % 4],
    certificate: "Chứng chỉ chuyên môn minh họa • 2025",
    status: i === 8 ? "Nghỉ phép" : "Đang làm việc",
    salary: 12000000 + i * 500000,
  }));
  const students: Database["students"] = names.map((name, i) => ({
    id: "HS" + String(i + 1).padStart(3, "0"),
    name,
    birthDate: `${year - 3 - (i % 5)}-${String((i % 12) + 1).padStart(2, "0")}-12`,
    status:
      i === 12 || i === 23 ? "Tạm nghỉ" : i === 29 ? "Ngừng học" : "Đang học",
    teacherId: staff[i % 8].id,
    parentId: "PH" + String(i + 1).padStart(3, "0"),
    joined: new Date(
      Date.UTC(
        year,
        Number(month.slice(5)) -
          1 -
          (i < 8 ? 0 : i < 14 ? 1 : i < 19 ? 2 : i < 23 ? 3 : i < 27 ? 4 : 5),
        5,
      ),
    )
      .toISOString()
      .slice(0, 10),
    note: "Hồ sơ minh họa, không phải thông tin trẻ thật.",
  }));
  const parents = students.map((s, i) => ({
    id: s.parentId,
    name: "Phụ huynh minh họa " + String(i + 1).padStart(2, "0"),
    email: `ph${i + 1}@example.invalid`,
    phone: "000 000 " + String(i + 1).padStart(4, "0"),
    studentIds: i === 0 ? [students[0].id, students[1].id] : [s.id],
  }));
  const classNames = [
    "Mầm Xanh 01",
    "Mầm Xanh 02",
    "Mầm Xanh 03",
    "Cầu Vồng",
    "Nắng Mai",
    "Hướng Dương",
    "Mặt Trời",
    "Ngôi Sao",
  ];
  const classes: Database["classes"] = classNames.map((name, i) => ({
    id: "LH" + String(i + 1).padStart(3, "0"),
    name,
    type: (i < 3 ? "Cá nhân" : i < 6 ? "Lớp nhóm" : "Bán trú") as StudyType,
    teacherId: staff[i].id,
    studentIds:
      i < 3
        ? [students[i].id]
        : students
            .filter((_, j) => (i < 6 ? j % 3 === i - 3 : j % 2 === i - 6))
            .map((s) => s.id),
    capacity: i < 3 ? 1 : i < 6 ? 12 : 18,
    status: "Đang hoạt động",
  }));
  const sessions: Database["sessions"] = [];
  const days = new Date(year, Number(month.slice(5)), 0).getDate();
  for (let d = 1; d <= days; d++) {
    const date = `${month}-${String(d).padStart(2, "0")}`;
    if ([0, 6].includes(new Date(date + "T12:00:00").getDay())) continue;
    classes.forEach((c, i) =>
      sessions.push({
        id: `BH${d}-${i}`,
        classId: c.id,
        teacherId: c.teacherId,
        date,
        start: i < 3 ? "08:00" : i < 6 ? "09:30" : "13:00",
        end: i < 3 ? "09:00" : i < 6 ? "10:30" : "16:00",
        room: "Phòng " + (i + 1),
        attendance:
          date < now
            ? Object.fromEntries(
                c.studentIds.map((s, j) => [
                  s,
                  j % 7 === 6 ? "Vắng" : "Có mặt",
                ]),
              )
            : {},
      }),
    );
  }
  return {
    version: 1,
    studentGoals: [],
    students,
    staff,
    parents,
    classes,
    sessions,
    fees: students.map((s, i) => ({
      id: "HP" + (i + 1),
      studentId: s.id,
      title: "Học phí tháng " + Number(month.slice(5)),
      month,
      amount: 2400000 + (i % 3) * 600000,
      paidAt: i % 3 === 0 ? null : month + "-05",
    })),
    leaves: [
      {
        id: "NP1",
        teacherId: staff[1].id,
        from: month + "-20",
        to: month + "-21",
        reason: "Việc gia đình (minh họa)",
        status: "Chờ duyệt",
      },
      {
        id: "NP2",
        teacherId: staff[8].id,
        from: month + "-16",
        to: month + "-16",
        reason: "Nghỉ phép cá nhân (minh họa)",
        status: "Chờ duyệt",
      },
    ],
    notices: [
      {
        id: "TB1",
        title: "Lịch sinh hoạt cùng phụ huynh tháng này",
        date: now,
        body: "Trung tâm tổ chức buổi trao đổi giữa giáo viên và phụ huynh vào thứ Bảy cuối tháng, từ 09:00 đến 11:00. Đây là thông báo minh họa.",
      },
      {
        id: "TB2",
        title: "Cập nhật lịch học và điểm danh",
        date: month + "-10",
        body: "Phụ huynh có thể theo dõi lịch học, chuyên cần và học phí của con ngay trên hệ thống demo.",
      },
    ],
    checkins: {},
    admins: [
      { id: "AD001", name: "Nguyễn Minh Anh", email: "admin@example.invalid" },
      { id: "AD002", name: "Trần Anh Dương", email: "admin2@example.invalid" },
    ],
  };
}
