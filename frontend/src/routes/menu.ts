import type { Role } from "../types";
export type MenuGroup = {
  label: string;
  icon: string;
  items: { label: string; path: string }[];
};
const items = (pairs: string[][]) =>
  pairs.map(([label, path]) => ({ label, path }));
export const adminMenu: MenuGroup[] = [
  {
    label: "Quản lý hệ thống",
    icon: "system",
    items: items([
      ["Thống kê tổng quan", "/dashboard"],
      ["Biểu đồ thống kê", "/analytics"],
      ["Tài khoản và phân quyền", "/accounts"],
    ]),
  },
  {
    label: "Quản lý cơ sở",
    icon: "building",
    items: items([
      ["Thống kê cơ sở", "/facility"],
      ["Quản lý học phí", "/fees"],
      ["Thông báo", "/notifications"],
      ["Biểu mẫu đánh giá", "/evaluation"],
      ["Ngày nghỉ/lễ", "/holidays"],
      ["Sinh nhật", "/birthdays"],
      ["Phiếu khảo sát", "/surveys"],
      ["Kho tài liệu", "/documents"],
      ["Thiết lập cơ sở", "/settings"],
    ]),
  },
  {
    label: "Quản lý hồ sơ",
    icon: "folder",
    items: items([
      ["Hồ sơ nhân sự", "/staff"],
      ["Hồ sơ học sinh", "/students"],
      ["Hồ sơ phụ huynh", "/parents"],
    ]),
  },
  {
    label: "Quản lý lớp cá nhân",
    icon: "individual",
    items: items([
      ["Phân công giáo viên", "/individual/assign"],
      ["Kế hoạch can thiệp", "/individual/plan"],
      ["Báo cáo cá nhân", "/individual/report"],
    ]),
  },
  {
    label: "Quản lý lớp bán trú",
    icon: "daycare",
    items: items([
      ["Danh sách lớp bán trú", "/daycare"],
      ["Thực đơn bán trú", "/daycare/menu"],
      ["Theo dõi sinh hoạt, ăn và ngủ", "/daycare/living"],
      ["Điểm danh bán trú", "/attendance/daycare"],
    ]),
  },
  {
    label: "Quản lý lớp nhóm",
    icon: "groups",
    items: items([
      ["Danh sách lớp nhóm", "/groups"],
      ["Học sinh tham gia", "/groups/students"],
      ["Phân công giáo viên", "/groups/assign"],
      ["Kế hoạch nhóm", "/groups/plan"],
      ["Báo cáo nhóm", "/groups/report"],
    ]),
  },
  {
    label: "Lịch học",
    icon: "calendar",
    items: items([
      ["Lịch ngày", "/schedule"],
      ["Thời khóa biểu", "/schedule/week"],
      ["Điều phối lịch học", "/schedule/manage"],
      ["Thống kê chuyên cần", "/attendance"],
    ]),
  },
  {
    label: "Thư viện",
    icon: "library",
    items: items([
      ["Thư viện nội bộ", "/library"],
      ["Thư viện tham khảo", "/library/reference"],
    ]),
  },
];
export const teacherMenu: MenuGroup[] = [
  {
    label: "Không gian giáo viên",
    icon: "system",
    items: items([
      ["Tổng quan cá nhân", "/dashboard"],
      ["Học sinh được phân công", "/students"],
      ["Kế hoạch can thiệp", "/individual/plan"],
      ["Lớp cá nhân phụ trách", "/individual"],
      ["Lớp nhóm phụ trách", "/groups"],
      ["Lớp bán trú phụ trách", "/daycare"],
      ["Lịch dạy", "/schedule"],
      ["Điểm danh học sinh", "/attendance"],
    ]),
  },
  {
    label: "Công việc của tôi",
    icon: "folder",
    items: items([
      ["Hồ sơ công việc cá nhân", "/work"],
      ["Chấm công", "/work/checkin"],
      ["Xin nghỉ phép", "/leave"],
      ["Tài liệu chuyên môn", "/library"],
    ]),
  },
];
export const parentMenu: MenuGroup[] = [
  {
    label: "Đồng hành cùng con",
    icon: "system",
    items: items([
      ["Tổng quan phụ huynh", "/dashboard"],
      ["Hồ sơ con", "/students"],
      ["Nhận xét kế hoạch", "/reports"],
      ["Lịch học của con", "/schedule"],
      ["Điểm danh và chuyên cần", "/attendance"],
      ["Thông báo", "/notifications"],
      ["Học phí", "/fees"],
      ["Lịch sử thanh toán", "/payments"],
      ["Thông tin lớp bán trú", "/daycare"],
    ]),
  },
];
export const getMenu = (role: Role) =>
  role === "ADMIN" ? adminMenu : role === "TEACHER" ? teacherMenu : parentMenu;
export const titles = Object.fromEntries(
  [...adminMenu, ...teacherMenu, ...parentMenu]
    .flatMap((g) => g.items)
    .map((i) => [i.path, i.label]),
);
