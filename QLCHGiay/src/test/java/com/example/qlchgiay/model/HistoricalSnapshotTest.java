package com.example.qlchgiay.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HistoricalSnapshotTest {

    @Test
    void issuedInvoiceDisplayDoesNotChangeWhenMasterDataIsRenamed() {
        KhachHang customer = new KhachHang();
        customer.setTenKH("Khách hiện tại");
        customer.setSoDienThoai("0900000000");
        DonHang order = new DonHang();
        order.setMaKH(customer);
        NhanVien employee = new NhanVien();
        employee.setTenNhanVien("Nhân viên hiện tại");
        HoaDon invoice = new HoaDon();
        invoice.setMaDonHang(order);
        invoice.setMaNhanVien(employee);
        invoice.setTenKhachHangSnapshot("Khách lúc bán");
        invoice.setSoDienThoaiKhachHangSnapshot("0911111111");
        invoice.setTenNhanVienSnapshot("Nhân viên lúc bán");

        customer.setTenKH("Khách đã đổi tên");
        customer.setSoDienThoai("0999999999");
        employee.setTenNhanVien("Nhân viên đã đổi tên");

        assertEquals("Khách lúc bán", invoice.getTenKhachHangHienThi());
        assertEquals("0911111111", invoice.getSoDienThoaiKhachHangHienThi());
        assertEquals("Nhân viên lúc bán", invoice.getTenNhanVienHienThi());
    }

    @Test
    void issuedLineDisplayDoesNotChangeWhenProductOrPromotionIsRenamed() {
        SanPham product = new SanPham();
        product.setTenSP("Tên sản phẩm mới");
        ChiTietSanPham productDetail = new ChiTietSanPham();
        productDetail.setMaSP(product);
        KhuyenMai promotion = new KhuyenMai();
        promotion.setTenKhuyenMai("Tên khuyến mại mới");
        ChiTietHoaDon line = new ChiTietHoaDon();
        line.setMaChiTietSP(productDetail);
        line.setMaKhuyenMai(promotion);
        line.setTenSanPhamSnapshot("Tên sản phẩm lúc bán");
        line.setTenKhuyenMaiSnapshot("Khuyến mại lúc bán");

        assertEquals("Tên sản phẩm lúc bán", line.getTenSanPhamHienThi());
        assertEquals("Khuyến mại lúc bán", line.getTenKhuyenMaiHienThi());
    }
}
