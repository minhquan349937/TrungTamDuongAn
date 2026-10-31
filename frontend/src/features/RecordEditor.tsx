import { Form, Input, InputNumber, Modal, Select } from "antd";
import { useEffect } from "react";
import { useDemo } from "../hooks/useDemo";
import { mockService } from "../services/mockService";
import type { Collection, Entity, SchoolClass, Session } from "../types";
import { today } from "../utils/format";
const labels: Record<Collection, string> = {
  students: "học sinh",
  staff: "nhân viên",
  classes: "lớp học",
  sessions: "lịch học",
  fees: "khoản thu",
  leaves: "đơn nghỉ phép",
};
export function RecordEditor({
  kind,
  record,
  onClose,
  initialType,
}: {
  kind: Collection;
  record?: Entity;
  onClose: () => void;
  initialType?: string;
}) {
  const { db, role, run } = useDemo();
  const [form] = Form.useForm();
  useEffect(() => {
    form.setFieldsValue(
      record ?? {
        status:
          kind === "students"
            ? "Đang học"
            : kind === "staff"
              ? "Đang làm việc"
              : kind === "classes"
                ? "Đang hoạt động"
                : "Chờ duyệt",
        teacherId: role === "TEACHER" ? "NV001" : db.staff[0]?.id,
        parentId: db.parents[0]?.id,
        joined: today(),
        type: initialType ?? "Cá nhân",
        capacity: 1,
        studentIds: [],
        date: today(),
        start: "08:00",
        end: "09:00",
        month: today().slice(0, 7),
        from: today(),
        to: today(),
        amount: 2400000,
        salary: 12000000,
        attendance: {},
        paidAt: null,
      },
    );
  }, [record, form, kind, db, role, initialType]);
  const req = [{ required: true, message: "Vui lòng nhập thông tin này." }];
  function save(values: Record<string, unknown>) {
    const staffCodeKey = "duongan.staff-code-seq.v1";
    const nextStaffCode = () => {
      const maxExisting = db.staff.reduce((max, staff) => {
        const match = /^NV(\d+)$/.exec(staff.id);
        return match ? Math.max(max, Number(match[1])) : max;
      }, 0);
      const reserved = Number(localStorage.getItem(staffCodeKey)) || 1;
      const next = Math.max(reserved, maxExisting + 1);
      localStorage.setItem(staffCodeKey, String(next + 1));
      return `NV${String(next).padStart(3, "0")}`;
    };
    const entity = {
      ...record,
      ...values,
      id: record?.id ?? (kind === "staff" ? nextStaffCode() : mockService.newId(
          {
            students: "HS",
            staff: "NV",
            classes: "LH",
            sessions: "BH",
            fees: "HP",
            leaves: "NP",
          }[kind],
        )),
    };
    if (kind === "sessions") {
      const session = entity as unknown as Session;
      const cls = db.classes.find((c) => c.id === session.classId);
      if (!record) session.attendance = {};
      if ((record as Session)?.classId !== session.classId)
        session.attendance = {};
      session.teacherId = cls?.teacherId ?? "";
    }
    if (kind === "students" && !record)
      Object.assign(entity, { note: values.note ?? "", joined: today() });
    if (kind === "fees" && !record) Object.assign(entity, { paidAt: null });
    if (kind === "leaves") Object.assign(entity, { status: "Chờ duyệt" });
    if (
      run(
        (data) =>
          mockService.save(data, role, kind, entity as unknown as Entity),
        record ? "Đã cập nhật " + labels[kind] : "Đã thêm " + labels[kind],
      )
    )
      onClose();
  }
  const teacherOptions = db.staff.map((s) => ({ value: s.id, label: s.name }));
  return (
    <Modal
      open
      title={(record ? "Chỉnh sửa " : "Thêm ") + labels[kind]}
      onCancel={onClose}
      onOk={() => form.submit()}
      okText={record ? "Lưu thay đổi" : "Tạo mới"}
      cancelText="Hủy"
      width={620}
      destroyOnClose
    >
      <p className="form-intro">
        {kind === "staff" && !record
          ? "Mã nhân viên sẽ được tự sinh theo thứ tự khi lưu. Thông tin được lưu trên trình duyệt cho mục đích minh họa."
          : "Thông tin chỉ được lưu trên trình duyệt cho mục đích minh họa."}
      </p>
      <Form form={form} layout="vertical" onFinish={save}>
        {(kind === "students" || kind === "staff" || kind === "classes") && (
          <Form.Item
            name="name"
            label={kind === "classes" ? "Tên lớp học" : "Họ và tên"}
            rules={[
              ...req,
              {
                whitespace: true,
                message: "Không được chỉ nhập khoảng trắng.",
              },
            ]}
          >
            <Input
              maxLength={100}
              placeholder={
                kind === "classes" ? "Ví dụ: Mầm Xanh 04" : "Nhập họ và tên"
              }
            />
          </Form.Item>
        )}
        {kind === "students" && (
          <>
            <div className="form-grid">
              <Form.Item name="birthDate" label="Ngày sinh" rules={req}>
                <Input type="date" max={today()} />
              </Form.Item>
              <Form.Item name="status" label="Trạng thái" rules={req}>
                <Select
                  options={["Đang học", "Tạm nghỉ", "Ngừng học"].map(
                    (value) => ({ value, label: value }),
                  )}
                />
              </Form.Item>
            </div>
            <Form.Item name="teacherId" label="Giáo viên phụ trách" rules={req}>
              <Select options={teacherOptions} />
            </Form.Item>
            <Form.Item name="parentId" label="Phụ huynh liên kết" rules={req}>
              <Select
                showSearch
                optionFilterProp="label"
                options={db.parents.map((p) => ({
                  value: p.id,
                  label: p.name,
                }))}
              />
            </Form.Item>
            <Form.Item name="note" label="Ghi chú">
              <Input.TextArea rows={3} />
            </Form.Item>
          </>
        )}
        {kind === "staff" && (
          <>
            <Form.Item name="specialty" label="Chuyên môn" rules={req}>
              <Select
                options={[
                  "Giáo dục đặc biệt",
                  "Âm ngữ trị liệu",
                  "Tâm lý giáo dục",
                  "Hoạt động trị liệu",
                  "Hành chính",
                ].map((value) => ({ value, label: value }))}
              />
            </Form.Item>
            <Form.Item name="certificate" label="Chứng chỉ">
              <Input />
            </Form.Item>
            <div className="form-grid">
              <Form.Item name="status" label="Trạng thái" rules={req}>
                <Select
                  options={["Đang làm việc", "Nghỉ phép", "Ngừng làm việc"].map(
                    (value) => ({ value, label: value }),
                  )}
                />
              </Form.Item>
              <Form.Item name="salary" label="Lương cơ bản (VNĐ)" rules={req}>
                <InputNumber min={0} step={500000} style={{ width: "100%" }} />
              </Form.Item>
            </div>
          </>
        )}
        {kind === "classes" && (
          <>
            <div className="form-grid">
              <Form.Item name="type" label="Hình thức" rules={req}>
                <Select
                  onChange={(v) =>
                    form.setFieldValue("capacity", v === "Cá nhân" ? 1 : 12)
                  }
                  options={["Cá nhân", "Lớp nhóm", "Bán trú"].map((value) => ({
                    value,
                    label: value,
                  }))}
                />
              </Form.Item>
              <Form.Item name="capacity" label="Sức chứa tối đa" rules={req}>
                <InputNumber min={1} max={50} />
              </Form.Item>
            </div>
            <Form.Item name="teacherId" label="Giáo viên phụ trách" rules={req}>
              <Select options={teacherOptions} />
            </Form.Item>
            <Form.Item name="studentIds" label="Học sinh tham gia">
              <Select
                mode="multiple"
                showSearch
                optionFilterProp="label"
                options={db.students.map((s) => ({
                  value: s.id,
                  label: s.name,
                }))}
              />
            </Form.Item>
            <Form.Item name="status" label="Trạng thái" rules={req}>
              <Select
                options={["Đang hoạt động", "Tạm dừng"].map((value) => ({
                  value,
                  label: value,
                }))}
              />
            </Form.Item>
          </>
        )}
        {kind === "sessions" && (
          <>
            <Form.Item
              name="classId"
              label="Lớp học (giáo viên theo phân công của lớp)"
              rules={req}
            >
              <Select
                options={db.classes
                  .filter((c) => c.status === "Đang hoạt động")
                  .map((c) => ({
                    value: c.id,
                    label:
                      c.name +
                      " · " +
                      db.staff.find((t) => t.id === c.teacherId)?.name,
                  }))}
              />
            </Form.Item>
            <div className="form-grid">
              <Form.Item name="date" label="Ngày học" rules={req}>
                <Input type="date" />
              </Form.Item>
              <Form.Item
                name="room"
                label="Phòng học"
                rules={[
                  ...req,
                  { whitespace: true, message: "Nhập tên phòng hợp lệ." },
                ]}
              >
                <Input placeholder="Phòng 1" />
              </Form.Item>
              <Form.Item name="start" label="Bắt đầu" rules={req}>
                <Input type="time" />
              </Form.Item>
              <Form.Item name="end" label="Kết thúc" rules={req}>
                <Input type="time" />
              </Form.Item>
            </div>
            <div className="info-note">
              Hệ thống kiểm tra trùng giờ của giáo viên, học sinh, lớp và phòng
              học.
            </div>
          </>
        )}
        {kind === "fees" && (
          <>
            <Form.Item name="studentId" label="Học sinh" rules={req}>
              <Select
                showSearch
                optionFilterProp="label"
                options={db.students.map((s) => ({
                  value: s.id,
                  label: s.name,
                }))}
              />
            </Form.Item>
            <Form.Item name="title" label="Nội dung khoản thu" rules={req}>
              <Input />
            </Form.Item>
            <div className="form-grid">
              <Form.Item name="month" label="Tháng học phí" rules={req}>
                <Input type="month" />
              </Form.Item>
              <Form.Item name="amount" label="Số tiền (VNĐ)" rules={req}>
                <InputNumber
                  min={1000}
                  step={100000}
                  style={{ width: "100%" }}
                />
              </Form.Item>
            </div>
          </>
        )}
        {kind === "leaves" && (
          <>
            <Form.Item name="teacherId" label="Nhân viên" rules={req}>
              <Select disabled={role === "TEACHER"} options={teacherOptions} />
            </Form.Item>
            <div className="form-grid">
              <Form.Item name="from" label="Từ ngày" rules={req}>
                <Input type="date" min={today()} />
              </Form.Item>
              <Form.Item name="to" label="Đến ngày" rules={req}>
                <Input type="date" min={today()} />
              </Form.Item>
            </div>
            <Form.Item
              name="reason"
              label="Lý do nghỉ phép"
              rules={[
                ...req,
                { whitespace: true, message: "Vui lòng ghi lý do." },
              ]}
            >
              <Input.TextArea rows={3} />
            </Form.Item>
          </>
        )}
      </Form>
    </Modal>
  );
}
